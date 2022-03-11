package gov.cms.model.rda.codegen.plugin.transformer;

import static gov.cms.model.rda.codegen.plugin.transformer.AbstractFieldTransformer.DEST_VAR;
import static gov.cms.model.rda.codegen.plugin.transformer.TransformerUtil.capitalize;

import com.squareup.javapoet.CodeBlock;
import gov.cms.model.rda.codegen.plugin.model.ColumnBean;
import gov.cms.model.rda.codegen.plugin.model.TransformationBean;

public class StandardTargetCodeGenerator implements TargetCodeGenerator {
  @Override
  public CodeBlock createSetRef(ColumnBean column, TransformationBean transformation) {
    return CodeBlock.of("$L::set$L", DEST_VAR, capitalize(column.getName()));
  }

  @Override
  public CodeBlock createSetCall(
      ColumnBean column, TransformationBean transformation, CodeBlock value) {
    return CodeBlock.builder()
        .addStatement("$L.set$L($L)", DEST_VAR, capitalize(column.getName()), value)
        .build();
  }
}
