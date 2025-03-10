package gov.cms.model.dsl.codegen.plugin.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.squareup.javapoet.ClassName;
import javax.persistence.FetchType;
import org.junit.jupiter.api.Test;

/** Unit tests for the {@link JoinBean} class. */
public class JoinBeanTest {
  /** Verify the login in various accessor methods. */
  @Test
  public void testAccessors() {
    JoinBean bean = new JoinBean();
    assertFalse(bean.isValidEntityClass());
    bean.setEntityClass("a");
    assertFalse(bean.isValidEntityClass());
    bean.setEntityClass(String.class.getName());
    assertTrue(bean.isValidEntityClass());
    assertEquals(ClassName.get(String.class), bean.getEntityClassType());

    assertFalse(bean.hasColumnName());
    bean.setJoinColumnName("x");
    assertTrue(bean.hasColumnName());

    assertFalse(bean.hasComment());
    bean.setComment("x");
    assertTrue(bean.hasComment());

    assertFalse(bean.hasMappedBy());
    bean.setMappedBy("x");
    assertTrue(bean.hasMappedBy());

    assertFalse(bean.hasOrderBy());
    bean.setOrderBy("x");
    assertTrue(bean.hasOrderBy());

    assertFalse(bean.hasForeignKey());
    bean.setForeignKey("x");
    assertTrue(bean.hasForeignKey());

    assertFalse(bean.isFetchTypeRequired());
    bean.setFetchType(FetchType.EAGER);
    assertTrue(bean.isFetchTypeRequired());
  }
}
