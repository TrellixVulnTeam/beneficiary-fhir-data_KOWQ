package gov.cms.model.rda.codegen.plugin.transformer;

import static gov.cms.model.rda.codegen.plugin.transformer.AbstractFieldTransformer.DEST_VAR;
import static gov.cms.model.rda.codegen.plugin.transformer.TransformerUtil.capitalize;

import com.squareup.javapoet.CodeBlock;
import gov.cms.model.rda.codegen.plugin.model.ColumnBean;
import gov.cms.model.rda.codegen.plugin.model.TransformationBean;
import java.util.Optional;

public class OptionalTargetCodeGenerator extends StandardTargetCodeGenerator {
  @Override
  public CodeBlock createSetRef(ColumnBean column, TransformationBean transformation) {
    if (column.isNullable()) {
      return CodeBlock.of(
          "value -> $L.set$L($T.ofNullable(value))",
          DEST_VAR,
          capitalize(column.getName()),
          Optional.class);
    } else {
      return super.createSetRef(column, transformation);
    }
  }

  @Override
  public CodeBlock createSetCall(
      ColumnBean column, TransformationBean transformation, CodeBlock value) {
    if (column.isNullable()) {
      return CodeBlock.builder()
          .addStatement(
              "$L.set$L($T.ofNullable(value))", DEST_VAR, capitalize(column.getName()), value)
          .build();
    } else {
      return super.createSetCall(column, transformation, value);
    }
  }
}
