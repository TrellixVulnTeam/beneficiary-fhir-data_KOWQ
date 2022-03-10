package gov.cms.model.rda.codegen.plugin;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import gov.cms.model.rda.codegen.plugin.model.ArrayElement;
import gov.cms.model.rda.codegen.plugin.model.ColumnBean;
import gov.cms.model.rda.codegen.plugin.model.EnumTypeBean;
import gov.cms.model.rda.codegen.plugin.model.JoinBean;
import gov.cms.model.rda.codegen.plugin.model.MappingBean;
import gov.cms.model.rda.codegen.plugin.model.ModelUtil;
import gov.cms.model.rda.codegen.plugin.model.RootBean;
import gov.cms.model.rda.codegen.plugin.model.TableBean;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import javax.lang.model.element.Modifier;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.ForeignKey;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.JoinColumn;
import javax.persistence.OrderBy;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.experimental.FieldNameConstants;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.hibernate.annotations.BatchSize;

/** A Maven Mojo that generates code for RDA API JPA entities. */
@Mojo(name = "entities", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class RdaEntityCodeGenMojo extends AbstractMojo {
  // region Fields
  private static final String PRIMARY_KEY_CLASS_NAME = "PK";
  private static final int BATCH_SIZE_FOR_ARRAY_FIELDS = 100;

  @Parameter(property = "mappingFile")
  private String mappingFile;

  @Parameter(
      property = "outputDirectory",
      defaultValue = "${project.build.directory}/generated-sources/rda-entities")
  private String outputDirectory;

  @Parameter(property = "project", readonly = true)
  private MavenProject project;
  // endregion

  @SneakyThrows(IOException.class)
  public void execute() throws MojoExecutionException {
    if (mappingFile == null || !new File(mappingFile).isFile()) {
      throw failure("mappingFile not defined or does not exist");
    }

    File outputDir = new File(outputDirectory);
    outputDir.mkdirs();
    RootBean root = ModelUtil.loadMappingsFromYamlFile(mappingFile);
    List<MappingBean> rootMappings = root.getMappings();
    for (MappingBean mapping : rootMappings) {
      TypeSpec rootEntity =
          createEntityFromMapping(
              mapping, root::findMappingWithId, root::findMappingWithEntityClassName);
      JavaFile javaFile = JavaFile.builder(mapping.entityPackageName(), rootEntity).build();
      javaFile.writeTo(outputDir);
    }
    project.addCompileSourceRoot(outputDirectory);
  }

  // region Implementation Details
  private TypeSpec createEntityFromMapping(
      MappingBean mapping,
      Function<String, Optional<MappingBean>> findMappingWithId,
      Function<String, Optional<MappingBean>> findMappingWithEntityClassName)
      throws MojoExecutionException {
    TypeSpec.Builder classBuilder =
        TypeSpec.classBuilder(mapping.entityClassName())
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Entity.class)
            .addAnnotation(Getter.class)
            .addAnnotation(Setter.class)
            .addAnnotation(Builder.class)
            .addAnnotation(AllArgsConstructor.class)
            .addAnnotation(NoArgsConstructor.class)
            .addAnnotation(createEqualsAndHashCodeAnnotation())
            .addAnnotation(FieldNameConstants.class);
    if (mapping.getTable().hasComment()) {
      classBuilder.addJavadoc(mapping.getTable().getComment());
    }
    if (!mapping.getTable().hasPrimaryKey()) {
      throw failure("mapping has no primary key fields: mapping=%s", mapping.getId());
    }
    classBuilder.addAnnotation(createTableAnnotation(mapping.getTable()));
    addEnums(mapping.getEnumTypes(), classBuilder);
    var primaryKeySpecs = new ArrayList<ColumnBean>();
    var primaryKeyFieldNames = Set.copyOf(mapping.getTable().getPrimaryKeyColumns());
    addPrimaryKeyFields(mapping, classBuilder, findMappingWithEntityClassName, primaryKeySpecs);
    addColumnFields(mapping, classBuilder, primaryKeyFieldNames);
    addArrayFields(mapping, findMappingWithId, classBuilder, primaryKeyFieldNames.size());
    addJoinFields(mapping, findMappingWithEntityClassName, classBuilder, primaryKeyFieldNames);
    if (primaryKeySpecs.size() > 1) {
      classBuilder
          .addAnnotation(createIdClassAnnotation(mapping))
          .addType(createPrimaryKeyClass(mapping, primaryKeySpecs));
    }
    return classBuilder.build();
  }

  private void addEnums(List<EnumTypeBean> enumMappings, TypeSpec.Builder classBuilder) {
    for (EnumTypeBean enumMapping : enumMappings) {
      classBuilder.addType(createEnum(enumMapping));
    }
  }

  private TypeSpec createEnum(EnumTypeBean mapping) {
    TypeSpec.Builder builder =
        TypeSpec.enumBuilder(mapping.getName()).addModifiers(Modifier.PUBLIC);
    for (String value : mapping.getValues()) {
      builder.addEnumConstant(value);
    }
    return builder.build();
  }

  private void addPrimaryKeyFields(
      MappingBean mapping,
      TypeSpec.Builder classBuilder,
      Function<String, Optional<MappingBean>> findMappingWithEntityClassName,
      List<ColumnBean> primaryKeySpecs)
      throws MojoExecutionException {
    for (String primaryKeyColumn : mapping.getTable().getPrimaryKeyColumns()) {
      var join = mapping.findJoinByFieldName(primaryKeyColumn);
      if (join.isPresent()) {
        addJoinField(mapping, join.get(), classBuilder);
        var keyColumn =
            findPrimaryKeyColumnBeanForJoin(mapping, findMappingWithEntityClassName, join.get());
        primaryKeySpecs.add(keyColumn);
        continue;
      }
      var column = mapping.findColumnByFieldName(primaryKeyColumn);
      if (column.isPresent()) {
        addColumnField(mapping, column.get(), classBuilder);
        primaryKeySpecs.add(column.get());
        continue;
      }
      throw failure(
          "no column or join matches primary key field name: mapping=%s field=%s",
          mapping.getId(), primaryKeyColumn);
    }
  }

  private void addJoinFields(
      MappingBean mapping,
      Function<String, Optional<MappingBean>> findMappingByEntityClass,
      TypeSpec.Builder classBuilder,
      Collection<String> namesToSkip)
      throws MojoExecutionException {
    for (JoinBean join : mapping.getTable().getJoins()) {
      if (namesToSkip.contains(join.getFieldName())) {
        continue;
      }
      if (isJoinForArray(mapping, join)) {
        continue;
      }
      addJoinField(mapping, join, classBuilder);
    }
  }

  private void addJoinField(MappingBean mapping, JoinBean join, TypeSpec.Builder classBuilder)
      throws MojoExecutionException {
    if (!join.isValidEntityClass()) {
      throw failure(
          "entityClass for join must include package: mapping=%s join=%s entityClass=%s",
          mapping.getId(), join.getFieldName(), join.getEntityClass());
    }
    if (isJoinForArray(mapping, join)) {
      throw failure(
          "array join field added as ordinary join: mapping=%s join=%s entityClass=%s",
          mapping.getId(), join.getFieldName(), join.getEntityClass());
    }
    final var fieldBuilder = createFieldSpecBuilderForJoin(mapping, join);
    classBuilder.addField(fieldBuilder.build());
  }

  private ColumnBean findPrimaryKeyColumnBeanForJoin(
      MappingBean mapping,
      Function<String, Optional<MappingBean>> findMappingByEntityClass,
      JoinBean join)
      throws MojoExecutionException {
    var parent = findMappingByEntityClass.apply(join.getEntityClass());
    if (parent.isEmpty()) {
      throw failure(
          "no mapping found for primary key join class: mapping=%s join=%s entityClass=%s",
          mapping.getId(), join.getFieldName(), join.getEntityClass());
    }
    return parent.get().getTable().findColumnByNameOrDbName(join.getJoinColumnName());
  }

  private void addColumnFields(
      MappingBean mapping, TypeSpec.Builder classBuilder, Collection<String> namesToSkip)
      throws MojoExecutionException {
    for (ColumnBean column : mapping.getTable().getColumns()) {
      if (!namesToSkip.contains(column.getName())) {
        addColumnField(mapping, column, classBuilder);
      }
    }
  }

  private void addColumnField(MappingBean mapping, ColumnBean column, TypeSpec.Builder classBuilder)
      throws MojoExecutionException {
    final var equalsFields = mapping.getTable().getColumnsForEqualsMethod();
    final TypeName fieldType = createFieldTypeForColumn(mapping, column);
    FieldSpec.Builder builder =
        FieldSpec.builder(fieldType, column.getName()).addModifiers(Modifier.PRIVATE);
    if (column.hasComment()) {
      builder.addJavadoc(column.getComment());
    }
    if (column.isEnum()) {
      builder.addAnnotation(createEnumeratedAnnotation(mapping, column));
    }
    if (column.getFieldType() == ColumnBean.FieldType.Transient) {
      builder.addAnnotation(Transient.class);
      if (mapping.getTable().isPrimaryKey(column.getName())) {
        throw failure(
            "transient fields cannot be primary keys: mapping=%s field=%s",
            mapping.getId(), column.getName());
      }
    } else {
      addColumnAnnotations(mapping, builder, column);
    }
    if (equalsFields.contains(column.getName())) {
      builder.addAnnotation(EqualsAndHashCode.Include.class);
    }
    FieldSpec fieldSpec = builder.build();
    classBuilder.addField(fieldSpec);
  }

  private void addColumnAnnotations(
      MappingBean mapping, FieldSpec.Builder builder, ColumnBean column)
      throws MojoExecutionException {
    if (column.isIdentity() && column.hasSequence()) {
      throw failure(
          "identity fields cannot have sequences: mapping=%s field=%s",
          mapping.getId(), column.getName());
    }
    if (column.getFieldType() == ColumnBean.FieldType.Transient
        && mapping.getTable().isPrimaryKey(column.getName())) {
      throw failure(
          "transient fields cannot be primary keys: mapping=%s field=%s",
          mapping.getId(), column.getName());
    }
    if (column.getFieldType() == ColumnBean.FieldType.Transient) {
      builder.addAnnotation(Transient.class);
    } else {
      if (mapping.getTable().isPrimaryKey(column.getName())) {
        builder.addAnnotation(Id.class);
      }
      builder.addAnnotation(createColumnAnnotation(column));
      if (column.isIdentity()) {
        builder.addAnnotation(
            AnnotationSpec.builder(GeneratedValue.class)
                .addMember("strategy", "$T.$L", GenerationType.class, GenerationType.IDENTITY)
                .build());
      } else if (column.hasSequence()) {
        builder
            .addAnnotation(
                AnnotationSpec.builder(GeneratedValue.class)
                    .addMember("strategy", "$T.$L", GenerationType.class, GenerationType.SEQUENCE)
                    .addMember("generator", "$S", column.getSequence().getName())
                    .build())
            .addAnnotation(
                AnnotationSpec.builder(SequenceGenerator.class)
                    .addMember("name", "$S", column.getSequence().getName())
                    .addMember("sequenceName", "$S", column.getSequence().getName())
                    .addMember("allocationSize", "$L", column.getSequence().getAllocationSize())
                    .build());
      }
    }
  }

  private FieldSpec.Builder createFieldSpecBuilderForJoin(MappingBean mapping, JoinBean join)
      throws MojoExecutionException {
    TypeName fieldType = join.getEntityClassType();
    if (join.getJoinType().isMultiValue()) {
      fieldType = ParameterizedTypeName.get(join.getCollectionType().getInterfaceName(), fieldType);
    }
    FieldSpec.Builder builder =
        FieldSpec.builder(fieldType, join.getFieldName()).addModifiers(Modifier.PRIVATE);
    if (mapping.getTable().isPrimaryKey(join)) {
      builder.addAnnotation(Id.class);
    }
    if (join.hasComment()) {
      builder.addJavadoc(join.getComment());
    }
    builder.addAnnotation(createJoinTypeAnnotation(mapping, join));
    if (join.hasColumnName()) {
      builder.addAnnotation(createJoinColumnAnnotation(mapping, join));
    }
    if (join.hasOrderBy()) {
      builder.addAnnotation(
          AnnotationSpec.builder(OrderBy.class)
              .addMember("value", "$S", join.getOrderBy())
              .build());
    }
    if (join.getJoinType().isMultiValue()) {
      builder
          .initializer("new $T<>()", join.getCollectionType().getClassName())
          .addAnnotation(
              AnnotationSpec.builder(BatchSize.class)
                  .addMember("size", "$L", BATCH_SIZE_FOR_ARRAY_FIELDS)
                  .build())
          .addAnnotation(Builder.Default.class);
    }
    return builder;
  }

  private AnnotationSpec createEnumeratedAnnotation(MappingBean mapping, ColumnBean column)
      throws MojoExecutionException {
    if (!column.isString()) {
      throw failure(
          "enum columns must have String type but this one does not: mapping=%s column=%s",
          mapping.getId(), column.getName());
    }
    return AnnotationSpec.builder(Enumerated.class)
        .addMember("value", "$T.$L", EnumType.class, EnumType.STRING)
        .build();
  }

  private void addArrayFields(
      MappingBean mapping,
      Function<String, Optional<MappingBean>> mappingFinder,
      TypeSpec.Builder classBuilder,
      int primaryKeyFieldCount)
      throws MojoExecutionException {
    if (mapping.getArrays().size() > 0 && primaryKeyFieldCount != 1) {
      throw failure(
          "classes with arrays must have a single primary key column but this one has %d: mapping=%s",
          primaryKeyFieldCount, mapping.getId());
    }
    for (ArrayElement arrayElement : mapping.getArrays()) {
      Optional<MappingBean> arrayMapping = mappingFinder.apply(arrayElement.getMapping());
      if (!arrayMapping.isPresent()) {
        throw failure(
            "array references unknown mapping: mapping=%s array=%s missing=%s",
            mapping.getId(), arrayElement.getTo(), arrayElement.getMapping());
      }
      addArrayField(
          mapping,
          classBuilder,
          mapping.getTable().getPrimaryKeyColumns().get(0),
          arrayElement,
          arrayMapping.get());
    }
  }

  private ClassName computePrimaryKeyClassName(MappingBean mapping) {
    return ClassName.get(
        mapping.entityPackageName(), mapping.entityClassName(), PRIMARY_KEY_CLASS_NAME);
  }

  private AnnotationSpec createIdClassAnnotation(MappingBean mapping) {
    return AnnotationSpec.builder(IdClass.class)
        .addMember("value", "$T.class", computePrimaryKeyClassName(mapping))
        .build();
  }

  private AnnotationSpec createJoinTypeAnnotation(MappingBean mapping, JoinBean join)
      throws MojoExecutionException {
    if (join.getJoinType() == null) {
      throw failure(
          "missing joinType: mapping=%s join=%s joinType=%s",
          mapping.getId(), join.getFieldName(), join.getJoinType());
    }
    final var annotationClass = join.getJoinType().getAnnotationClass();
    final var builder = AnnotationSpec.builder(annotationClass);
    if (join.hasMappedBy()) {
      builder.addMember("mappedBy", "$S", join.getMappedBy());
    }
    if (join.hasOrphanRemoval()) {
      builder.addMember("orphanRemoval", "$L", join.getOrphanRemoval());
    }
    if (join.isFetchTypeRequired()) {
      builder.addMember("fetch", "$T.$L", FetchType.class, join.getFetchType());
    }
    for (CascadeType cascadeType : join.getCascadeTypes()) {
      builder.addMember("cascade", "$T.$L", CascadeType.class, cascadeType);
    }
    return builder.build();
  }

  private AnnotationSpec createJoinColumnAnnotation(MappingBean mapping, JoinBean join)
      throws MojoExecutionException {
    if (!join.hasColumnName()) {
      throw failure(
          "missing joinColumnName: mapping=%s join=%s", mapping.getId(), join.getFieldName());
    }
    AnnotationSpec.Builder builder =
        AnnotationSpec.builder(JoinColumn.class)
            .addMember("name", "$S", quoteName(join.getJoinColumnName()));
    if (join.hasForeignKey()) {
      builder.addMember(
          "foreignKey",
          "$L",
          AnnotationSpec.builder(ForeignKey.class)
              .addMember("name", "$S", join.getForeignKey())
              .build());
    }
    return builder.build();
  }

  private boolean isJoinForArray(MappingBean mapping, JoinBean join) {
    for (ArrayElement arrayElement : mapping.getArrays()) {
      if (arrayElement.getTo().equals(join.getFieldName())) {
        return true;
      }
    }
    return false;
  }

  private JoinBean getJoinForArray(
      MappingBean mapping,
      String primaryKeyFieldName,
      ArrayElement arrayElement,
      MappingBean elementMapping) {
    for (JoinBean join : mapping.getTable().getJoins()) {
      if (join.getFieldName().equals(arrayElement.getTo())) {
        return join;
      }
    }
    return JoinBean.builder()
        .joinType(JoinBean.JoinType.OneToMany)
        .collectionType(JoinBean.CollectionType.Set)
        .fieldName(arrayElement.getTo())
        .entityClass(elementMapping.getEntityClassName())
        .fetchType(FetchType.EAGER)
        .orphanRemoval(true)
        .cascadeTypes(List.of(CascadeType.ALL))
        .mappedBy(primaryKeyFieldName)
        .build();
  }

  private TypeSpec createPrimaryKeyClass(MappingBean mapping, List<ColumnBean> columns) {
    TypeSpec.Builder pkClassBuilder =
        TypeSpec.classBuilder(PRIMARY_KEY_CLASS_NAME)
            .addSuperinterface(Serializable.class)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL, Modifier.STATIC)
            .addAnnotation(Data.class)
            .addAnnotation(NoArgsConstructor.class)
            .addAnnotation(AllArgsConstructor.class)
            .addJavadoc("PK class for the $L table", mapping.getTable().getName());
    for (ColumnBean column : columns) {
      final TypeName fieldType = createFieldTypeForColumn(mapping, column);
      FieldSpec.Builder keyFieldBuilder =
          FieldSpec.builder(fieldType, column.getName()).addModifiers(Modifier.PRIVATE);
      FieldSpec keyFieldSpec = keyFieldBuilder.build();
      pkClassBuilder.addField(keyFieldSpec);
    }
    return pkClassBuilder.build();
  }

  private void addArrayField(
      MappingBean mapping,
      TypeSpec.Builder classBuilder,
      String primaryKeyFieldName,
      ArrayElement arrayElement,
      MappingBean elementMapping)
      throws MojoExecutionException {
    final var join = getJoinForArray(mapping, primaryKeyFieldName, arrayElement, elementMapping);
    if (!join.getJoinType().isMultiValue()) {
      throw failure(
          "array mappings must have multi-value joins: array=%s joinType=%s",
          arrayElement.getTo(), join.getJoinType());
    }
    final var fieldBuilder = createFieldSpecBuilderForJoin(mapping, join);
    classBuilder.addField(fieldBuilder.build());
  }

  private AnnotationSpec createEqualsAndHashCodeAnnotation() {
    return AnnotationSpec.builder(EqualsAndHashCode.class)
        .addMember("onlyExplicitlyIncluded", "$L", true)
        .build();
  }

  private String quoteName(String name) {
    return "`" + name + "`";
  }

  private AnnotationSpec createTableAnnotation(TableBean table) {
    AnnotationSpec.Builder builder =
        AnnotationSpec.builder(Table.class).addMember("name", "$S", quoteName(table.getName()));
    if (table.hasSchema()) {
      builder.addMember("schema", "$S", quoteName(table.getSchema()));
    }
    return builder.build();
  }

  private AnnotationSpec createColumnAnnotation(ColumnBean column) {
    AnnotationSpec.Builder builder =
        AnnotationSpec.builder(Column.class)
            .addMember("name", "$S", quoteName(column.getColumnName()));
    if (!column.isNullable()) {
      builder.addMember("nullable", "$L", false);
    }
    if (!column.isUpdatable()) {
      builder.addMember("updatable", "$L", false);
    }
    if (column.isColumnDefRequired()) {
      builder.addMember("columnDefinition", "$S", column.getSqlType());
      var value = column.getPrecision();
      if (value > 0) {
        builder.addMember("precision", "$L", value);
      }
      value = column.getScale();
      if (value > 0) {
        builder.addMember("scale", "$L", value);
      }
    }
    int length = column.computeLength();
    if (length > 0 && length < Integer.MAX_VALUE) {
      builder.addMember("length", "$L", length);
    }
    return builder.build();
  }

  private TypeName createFieldTypeForColumn(MappingBean mapping, ColumnBean column) {
    TypeName fieldType;
    if (column.isEnum()) {
      fieldType =
          ClassName.get(
              mapping.entityPackageName(), mapping.entityClassName(), column.getEnumType());
    } else {
      fieldType = column.computeJavaType();
    }
    return fieldType;
  }
  // endregion

  private MojoExecutionException failure(String formatString, Object... args)
      throws MojoExecutionException {
    String message = String.format(formatString, args);
    return new MojoExecutionException(message);
  }
}
