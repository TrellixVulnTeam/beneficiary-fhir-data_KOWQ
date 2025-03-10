package gov.cms.bfd.server.war.r4.providers.pac.common;

import gov.cms.bfd.server.war.r4.providers.pac.AbstractR4ResourceProvider;
import java.util.List;
import javax.persistence.Entity;
import javax.persistence.Id;
import org.hl7.fhir.instance.model.api.IBaseResource;

/**
 * Interface to allow for more generic logic in {@link AbstractR4ResourceProvider}.
 *
 * @param <TResource> The base resource that the given type configuration is for.
 * @param <TEntity> the JPA entity class of objects used to generate a resource.
 */
public interface ResourceTypeV2<TResource extends IBaseResource, TEntity> {

  /** @return a name for use when building drop wizard metric names */
  String getNameForMetrics();

  /**
   * @return the JPA {@link Entity} {@link Class} used to store instances of this {@link
   *     ResourceTypeV2} in the database
   */
  Class<TEntity> getEntityClass();

  /** @return the JPA {@link Entity} field used as the entity's {@link Id} */
  String getEntityIdAttribute();

  /** @return The attribute name for the entity's mbiRecord attribute. */
  String getEntityMbiRecordAttribute();

  /**
   * List of attribute names for the entity's service start and/or end dates.
   *
   * @return List of attribute names for use in service date queries.
   */
  List<String> getEntityServiceDateAttributes();

  /**
   * @return the {@link ResourceTransformer} to use to transform the JPA {@link Entity} instances
   *     into FHIR instances
   */
  ResourceTransformer<TResource> getTransformer();
}
