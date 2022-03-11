package gov.cms.model.rda.codegen.plugin;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import java.util.Optional;
import javax.lang.model.element.Modifier;

public class PoetUtil {
  public static final ClassName OptionalClassName = ClassName.get(Optional.class);

  public static ClassName toClassName(String fullClassName) {
    final int lastComponentDotIndex = fullClassName.lastIndexOf('.');
    if (lastComponentDotIndex <= 0) {
      throw new IllegalArgumentException("expected a full class name but there was no .");
    }
    return ClassName.get(
        fullClassName.substring(0, lastComponentDotIndex),
        fullClassName.substring(lastComponentDotIndex + 1));
  }

  public static MethodSpec createStandardSetter(String propertyName, TypeName fieldType) {
    return MethodSpec.methodBuilder(fieldToMethodName("set", propertyName))
        .addModifiers(Modifier.PUBLIC)
        .addParameter(fieldType, propertyName)
        .addStatement("this.$N = $N", propertyName, propertyName)
        .build();
  }

  public static MethodSpec createStandardGetter(String propertyName, TypeName fieldType) {
    return MethodSpec.methodBuilder(fieldToMethodName("get", propertyName))
        .addModifiers(Modifier.PUBLIC)
        .returns(fieldType)
        .addStatement("return $N", propertyName)
        .build();
  }

  public static MethodSpec createOptionalSetter(String propertyName, TypeName fieldType) {
    return MethodSpec.methodBuilder(fieldToMethodName("set", propertyName))
        .addModifiers(Modifier.PUBLIC)
        .addParameter(ParameterizedTypeName.get(OptionalClassName, fieldType), propertyName)
        .addStatement("this.$N = $N.orElse(null)", propertyName, propertyName)
        .build();
  }

  public static MethodSpec createOptionalGetter(String propertyName, TypeName fieldType) {
    return MethodSpec.methodBuilder(fieldToMethodName("get", propertyName))
        .addModifiers(Modifier.PUBLIC)
        .returns(ParameterizedTypeName.get(OptionalClassName, fieldType))
        .addStatement("return $T.ofNullable($N)", Optional.class, propertyName)
        .build();
  }

  private static String fieldToMethodName(String prefix, String fieldName) {
    return prefix + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
  }
}
