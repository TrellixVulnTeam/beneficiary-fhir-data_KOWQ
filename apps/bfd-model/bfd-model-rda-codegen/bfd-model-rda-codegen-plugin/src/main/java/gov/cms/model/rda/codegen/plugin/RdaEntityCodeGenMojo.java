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
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import javax.lang.model.element.Modifier;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.JoinColumn;
import javax.persistence.OrderBy;
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
      TypeSpec rootEntity = createEntityFromMapping(mapping, root::findMappingWithId);
      JavaFile javaFile = JavaFile.builder(mapping.entityPackageName(), rootEntity).build();
      javaFile.writeTo(outputDir);
    }
    project.addCompileSourceRoot(outputDirectory);
  }

  // region Implementation Details
  private TypeSpec createEntityFromMapping(
      MappingBean mapping, Function<String, Optional<MappingBean>> mappingFinder)
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
    List<FieldSpec> primaryKeySpecs = new ArrayList<>();
    addColumnFields(mapping, classBuilder, primaryKeySpecs);
    if (primaryKeySpecs.size() > 1) {
      classBuilder
          .addAnnotation(createIdClassAnnotation(mapping))
          .addType(createPrimaryKeyClass(mapping, primaryKeySpecs));
    }
    addArrayFields(mapping, mappingFinder, classBuilder, primaryKeySpecs);
    addJoinFields(mapping, classBuilder);
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

  private void addColumnFields(
      MappingBean mapping, TypeSpec.Builder classBuilder, List<FieldSpec> primaryKeySpecs)
      throws MojoExecutionException {
    final var equalsFields = mapping.getTable().getColumnsForEqualsMethod();
    TypeName fieldType;
    for (ColumnBean column : mapping.getTable().getColumns()) {
      if (column.isEnum()) {
        fieldType =
            ClassName.get(
                mapping.entityPackageName(), mapping.entityClassName(), column.getEnumType());
      } else {
        fieldType = column.computeJavaType();
      }
      FieldSpec.Builder builder =
          FieldSpec.builder(fieldType, column.getName()).addModifiers(Modifier.PRIVATE);
      if (column.hasComment()) {
        builder.addJavadoc(column.getComment());
      }
      if (column.isEnum()) {
        builder.addAnnotation(createEnumeratedAnnotation(mapping, column));
      }
      if (column.getFieldType() == ColumnBean.FieldType.Transient) {
        addTransientAnnotations(mapping, builder, column);
      } else {
        addColumnAnnotations(mapping, builder, column);
      }
      if (equalsFields.contains(column.getName())) {
        builder.addAnnotation(EqualsAndHashCode.Include.class);
      }
      FieldSpec fieldSpec = builder.build();
      classBuilder.addField(fieldSpec);
      if (mapping.getTable().isPrimaryKey(column.getName())) {
        primaryKeySpecs.add(fieldSpec);
      }
    }
  }

  private void addColumnAnnotations(
      MappingBean mapping, FieldSpec.Builder builder, ColumnBean column) {
    if (mapping.getTable().isPrimaryKey(column.getName())) {
      builder.addAnnotation(Id.class);
      if (column.isIdentity()) {
        builder.addAnnotation(
            AnnotationSpec.builder(GeneratedValue.class)
                .addMember("strategy", "$T.$L", GenerationType.class, GenerationType.IDENTITY)
                .build());
      }
    }
    builder.addAnnotation(createColumnAnnotation(column));
  }

  private void addTransientAnnotations(
      MappingBean mapping, FieldSpec.Builder builder, ColumnBean column)
      throws MojoExecutionException {
    builder.addAnnotation(Transient.class);
    if (mapping.getTable().isPrimaryKey(column.getName())) {
      throw failure(
          "transient fields cannot be primary keys: mapping=%s field=%s",
          mapping.getId(), column.getName());
    }
  }

  private void addJoinFields(MappingBean mapping, TypeSpec.Builder classBuilder)
      throws MojoExecutionException {
    for (JoinBean join : mapping.getTable().getJoins()) {
      if (!join.isValidEntityClass()) {
        throw failure(
            "entityClass for join must include package: mapping=%s join=%s entityClass=%s",
            mapping.getId(), join.getFieldName(), join.getEntityClass());
      }
      if (isJoinForArray(mapping, join)) {
        continue;
      }
      final var fieldBuilder = createFieldSpecBuilderForJoin(mapping, join);
      classBuilder.addField(fieldBuilder.build());
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
      List<FieldSpec> primaryKeySpecs)
      throws MojoExecutionException {
    if (mapping.getArrays().size() > 0 && primaryKeySpecs.size() != 1) {
      throw failure(
          "classes with arrays must have a single primary key column but this one has %d: mapping=%s",
          primaryKeySpecs.size(), mapping.getId());
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
    if (join.getFetchType() == null) {
      throw failure(
          "missing fetchType: mapping=%s join=%s fetchType=%s",
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
    builder.addMember("fetch", "$T.$L", FetchType.class, join.getFetchType());
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
    return AnnotationSpec.builder(JoinColumn.class)
        .addMember("name", "$S", quoteName(join.getJoinColumnName()))
        .build();
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

  private TypeSpec createPrimaryKeyClass(MappingBean mapping, List<FieldSpec> parentKeySpecs) {
    TypeSpec.Builder pkClassBuilder =
        TypeSpec.classBuilder(PRIMARY_KEY_CLASS_NAME)
            .addSuperinterface(Serializable.class)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL, Modifier.STATIC)
            .addAnnotation(Data.class)
            .addAnnotation(NoArgsConstructor.class)
            .addAnnotation(AllArgsConstructor.class)
            .addJavadoc("PK class for the $L table", mapping.getTable().getName());
    for (FieldSpec fieldSpec : parentKeySpecs) {
      FieldSpec.Builder keyFieldBuilder =
          FieldSpec.builder(fieldSpec.type, fieldSpec.name).addModifiers(Modifier.PRIVATE);
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
  // endregion

  private MojoExecutionException failure(String formatString, Object... args)
      throws MojoExecutionException {
    String message = String.format(formatString, args);
    return new MojoExecutionException(message);
  }
}
