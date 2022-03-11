package gov.cms.model.rda.codegen.plugin.transformer;

import com.squareup.javapoet.CodeBlock;
import gov.cms.model.rda.codegen.plugin.model.ColumnBean;
import gov.cms.model.rda.codegen.plugin.model.MappingBean;
import gov.cms.model.rda.codegen.plugin.model.TransformationBean;

public class StringFieldTransformer extends AbstractFieldTransformer {
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
            "$L.copyString($L, $L, 1, $L, $L, $L)",
            TRANSFORMER_VAR,
            fieldNameReference(mapping, column),
            column.isNullable(),
            column.computeLength(),
            messageCodeGenerator.createGetCall(transformation),
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
            "$L.copyOptionalString($L, 1, $L, $L, $L, $L)",
            TRANSFORMER_VAR,
            fieldNameReference(mapping, column),
            column.computeLength(),
            messageCodeGenerator.createHasRef(transformation),
            messageCodeGenerator.createGetRef(transformation),
            destSetRef(column))
        .build();
  }
}
