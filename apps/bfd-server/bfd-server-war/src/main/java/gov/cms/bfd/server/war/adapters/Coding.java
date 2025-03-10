package gov.cms.bfd.server.war.adapters;

/**
 * Interface for creating Coding wrapper implementations for different FHIR resource
 * implementations.
 */
public interface Coding {

  /**
   * Gets the system.
   *
   * @return the system
   */
  String getSystem();

  /**
   * Gets the code.
   *
   * @return the code
   */
  String getCode();
}
