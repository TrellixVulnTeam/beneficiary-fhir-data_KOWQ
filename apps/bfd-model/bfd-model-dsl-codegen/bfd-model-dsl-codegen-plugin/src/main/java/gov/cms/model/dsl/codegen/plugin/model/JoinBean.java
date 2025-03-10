package gov.cms.model.dsl.codegen.plugin.model;

import com.google.common.base.Strings;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;
import gov.cms.model.dsl.codegen.plugin.model.validation.JavaName;
import gov.cms.model.dsl.codegen.plugin.model.validation.JavaNameType;
import gov.cms.model.dsl.codegen.plugin.model.validation.JavaType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import javax.persistence.CascadeType;
import javax.persistence.FetchType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Singular;

/** Model object for defining JPA compatible join relationships between entities. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JoinBean implements ModelBean {
  /** Name of the field in this entity object that holds the joined entity. */
  @NotNull @JavaName private String fieldName;

  /** Full class name of the joined entity. Must include the package as well as the class name. */
  @NotNull
  @JavaName(type = JavaNameType.Compound)
  private String entityClass;

  /** Name of the column in this entity that will hold the joined table's primary key. */
  @JavaName(type = JavaNameType.Compound)
  private String joinColumnName;

  /** Type of join annotation to apply to this field. */
  private JoinType joinType;

  /** Name of the {@link FetchType}. Either {@link FetchType#EAGER} or @{link FetchType.LAZY}. */
  private FetchType fetchType;

  /** Optional comment string to be added to the join field in the generated entity. */
  private String comment;

  /** Type of collection to use for storing joined objects. */
  @NotNull @Builder.Default private CollectionType collectionType = CollectionType.List;

  /** Value for {@code mappedBy} argument to annotation. */
  @JavaName private String mappedBy;

  /** Value for {@code orphanRemoval} argument to annotation. */
  private Boolean orphanRemoval;

  /** {@link CascadeType}s to use as argument to the annotation. */
  @NotNull @Builder.Default private List<CascadeType> cascadeTypes = new ArrayList<>();

  /**
   * Optionally specifies an order by expression to add using an {@link javax.persistence.OrderBy}
   * annotation.
   */
  private String orderBy;

  /**
   * Optionally specifies a foreign key constraint to reference in the {@link
   * javax.persistence.JoinColumn} annotation.
   */
  private String foreignKey;

  /** Value for {@code readOnly} argument to annotation. */
  @Builder.Default private boolean readOnly = false;

  /**
   * Optional list of properties to generate for single-value joins to simplify access to fields in
   * the joined entity.
   */
  @Singular private List<@Valid Property> properties = new ArrayList<>();

  /**
   * Determine if the entity class name is valid.
   *
   * @return true if the {@link JoinBean#entityClass} property has a valid value
   */
  public boolean isValidEntityClass() {
    return entityClass != null && entityClass.indexOf('.') > 0;
  }

  /**
   * Create a {@link TypeName} for the entity class.
   *
   * @return a valid {@link TypeName}
   */
  public TypeName getEntityClassType() {
    return ModelUtil.classType(entityClass);
  }

  /**
   * Determine if there is a join column name defined.
   *
   * @return true if a non-empty value is defined
   */
  public boolean hasColumnName() {
    return !Strings.isNullOrEmpty(joinColumnName);
  }

  /**
   * Determine if there is a javadoc comment defined.
   *
   * @return true if a non-empty value is defined
   */
  public boolean hasComment() {
    return !Strings.isNullOrEmpty(comment);
  }

  /**
   * Determine if there is a value defined for the {@code mappedBy} argument to the annotation.
   *
   * @return true if there is a value defined for the {@code mappedBy} argument to the annotation.
   */
  public boolean hasMappedBy() {
    return !Strings.isNullOrEmpty(mappedBy);
  }

  /**
   * Determine if there is a value defined for the {@code orphanRemoval} argument to the annotation.
   *
   * @return true if there is a value defined for the {@code orphanRemoval} argument to the
   *     annotation.
   */
  public boolean hasOrphanRemoval() {
    return orphanRemoval != null;
  }

  /**
   * Determine if there is a value defined for the {@code orderBy} argument to the annotation.
   *
   * @return true if there is a value defined for the {@code orderBy} argument to the annotation.
   */
  public boolean hasOrderBy() {
    return !Strings.isNullOrEmpty(orderBy);
  }

  /**
   * Determine if there is a value defined for the {@link javax.persistence.ForeignKey} annotation.
   *
   * @return true if there is a value defined for the {@link javax.persistence.ForeignKey}
   *     annotation.
   */
  public boolean hasForeignKey() {
    return !Strings.isNullOrEmpty(foreignKey);
  }

  /**
   * Determine if there is a value defined for the {@code fetchType} argument to the annotation.
   *
   * @return true if there is a value defined for the {@code fetchType} argument to the annotation.
   */
  public boolean isFetchTypeRequired() {
    return fetchType != null;
  }

  @Override
  public String getDescription() {
    return "join " + fieldName + " to " + entityClass;
  }

  /**
   * An enum type used to specify which annotation to use for the join and whether the join returns
   * a single value or multiple values.
   */
  @AllArgsConstructor
  public enum JoinType {
    /** OneToMany. */
    OneToMany(ClassName.get(javax.persistence.OneToMany.class), true),
    /** ManyToOne. */
    ManyToOne(ClassName.get(javax.persistence.ManyToOne.class), false),
    /** OneToOne. */
    OneToOne(ClassName.get(javax.persistence.OneToOne.class), false);

    /** The annotation class to use when adding the join annotation to the entity. */
    @Getter private final ClassName annotationClass;
    /** Whether the join has a single value or multiple values. */
    @Getter private final boolean multiValue;

    /**
     * Determine if the join returns a single value.
     *
     * @return true if the join returns a single value
     */
    public boolean isSingleValue() {
      return !multiValue;
    }
  }

  /**
   * An enum type used to specify which type of collection to use for joins that return multiple
   * values.
   */
  @AllArgsConstructor
  public enum CollectionType {
    /** List. */
    List(ClassName.get(List.class), ClassName.get(LinkedList.class)),
    /** Set. */
    Set(ClassName.get(Set.class), ClassName.get(HashSet.class));
    /** Collection interface used to declare the field in the entity class. */
    @Getter private final ClassName interfaceName;
    /** Collection class used to create an instance for the field in the entity class. */
    @Getter private final ClassName className;
  }

  /**
   * For single value joins we can define properties of the containing entity that are tied to a
   * field in the joined entity. This object holds the desired name of the property, the name of the
   * field within the joined object, and the java type of the field.
   */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Property {
    /**
     * Name of the property in the containing entity. The generated getter name will be {@code get}
     * followed by the capitalized {@link Property#name}.
     */
    @NotNull @JavaName private String name;

    /** Name of the field in the joined object to get a value from when the getter is called. */
    @NotNull @JavaName private String fieldName;

    /**
     * Indicates the java type to use for the return value of the getter. Values must be recognized
     * by {@link ModelUtil#mapJavaTypeToTypeName}.
     */
    @JavaType private String javaType;
  }
}
