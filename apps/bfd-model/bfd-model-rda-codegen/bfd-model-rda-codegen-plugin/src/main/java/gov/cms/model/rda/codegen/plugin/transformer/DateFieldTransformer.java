package gov.cms.model.rda.codegen.plugin.transformer;

import com.squareup.javapoet.CodeBlock;
import gov.cms.model.rda.codegen.plugin.model.ColumnBean;
import gov.cms.model.rda.codegen.plugin.model.MappingBean;
import gov.cms.model.rda.codegen.plugin.model.TransformationBean;

public class DateFieldTransformer extends AbstractFieldTransformer {
  @Override
  public CodeBlock generateCodeBlock(
      MappingBean mapping,
      ColumnBean column,
      TransformationBean transformation,
      MessageCodeGenerator messageCodeGenerator) {
    return transformation.isOptional()
        ? generateBlockForOptional(mapping, column, transformation, messageCodeGenerator)
        : generateBlockForRequired(mapping, column, transformation, messageCodeGenerator);
  }

  private CodeBlock generateBlockForRequired(
      MappingBean mapping,
      ColumnBean column,
      TransformationBean transformation,
      MessageCodeGenerator messageCodeGenerator) {
    return CodeBlock.builder()
        .addStatement(
            "$L.copyDate($L, $L, $L, $L)",
            TRANSFORMER_VAR,
            fieldNameReference(mapping, column),
            column.isNullable(),
            messageCodeGenerator.createGetValue(transformation),
            destSetRef(column))
        .build();
  }

  private CodeBlock generateBlockForOptional(
      MappingBean mapping,
      ColumnBean column,
      TransformationBean transformation,
      MessageCodeGenerator messageCodeGenerator) {
    return CodeBlock.builder()
        .addStatement(
            "$L.copyOptionalDate($L, $L, $L, $L)",
            TRANSFORMER_VAR,
            fieldNameReference(mapping, column),
            messageCodeGenerator.createHasRef(transformation),
            messageCodeGenerator.createGetRef(transformation),
            destSetRef(column))
        .build();
  }
}
