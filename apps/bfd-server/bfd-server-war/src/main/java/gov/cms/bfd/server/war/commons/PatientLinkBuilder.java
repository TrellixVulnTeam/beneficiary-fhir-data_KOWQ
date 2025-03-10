package gov.cms.bfd.server.war.commons;

import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import java.util.List;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.dstu3.model.Patient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

/** A link builder for Patient resources using bene-id cursors */
public final class PatientLinkBuilder implements LinkBuilder {
  /**
   * Maximum page size is one less than the maximum integer value to allow clients to request one
   * more than the page size as a means to see if an additional page is necessary without having an
   * integer overflow.
   */
  public static final int MAX_PAGE_SIZE = Integer.MAX_VALUE - 1;

  private final UriComponents components;
  private final Integer count;
  private final Long cursor;
  private final boolean hasAnotherPage;

  public static final String PARAM_CURSOR = "cursor";

  public PatientLinkBuilder(String requestString) {
    components = UriComponentsBuilder.fromUriString(requestString).build();
    count = extractCountParam(components);
    cursor = extractCursorParam(components);
    hasAnotherPage = false; // Don't really know, so default to false
    validate();
  }

  public PatientLinkBuilder(PatientLinkBuilder prev, boolean hasAnotherPage) {
    components = prev.components;
    count = prev.count;
    cursor = prev.cursor;
    this.hasAnotherPage = hasAnotherPage;
    validate();
  }

  /** Check that the page size is valid */
  private void validate() {
    if (getPageSize() <= 0) {
      throw new InvalidRequestException("A zero or negative page size is unsupported");
    }
    if (!(getPageSize() <= MAX_PAGE_SIZE)) {
      throw new InvalidRequestException("Page size must be less than " + MAX_PAGE_SIZE);
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean isPagingRequested() {
    return count != null;
  }

  /** {@inheritDoc} */
  @Override
  public int getPageSize() {
    return isPagingRequested() ? count : MAX_PAGE_SIZE;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isFirstPage() {
    return cursor == null || !isPagingRequested();
  }

  /** {@inheritDoc} */
  @Override
  public void addLinks(Bundle to) {
    List<BundleEntryComponent> entries = to.getEntry();
    if (!isPagingRequested()) {
      return;
    }
    to.addLink(
        new Bundle.BundleLinkComponent()
            .setRelation(Constants.LINK_SELF)
            .setUrl(components.toUriString()));
    to.addLink(
        new Bundle.BundleLinkComponent().setRelation(Constants.LINK_FIRST).setUrl(buildUrl(null)));

    if (hasAnotherPage) {
      Patient lastPatient = (Patient) entries.get(entries.size() - 1).getResource();
      Long lastPatientId = Long.parseLong(lastPatient.getId());
      to.addLink(
          new Bundle.BundleLinkComponent()
              .setRelation(Constants.LINK_NEXT)
              .setUrl(buildUrl(lastPatientId)));
    }
  }

  /** {@inheritDoc} */
  @Override
  public void addLinks(org.hl7.fhir.r4.model.Bundle to) {
    List<org.hl7.fhir.r4.model.Bundle.BundleEntryComponent> entries = to.getEntry();
    if (!isPagingRequested()) {
      return;
    }
    to.addLink(
        new org.hl7.fhir.r4.model.Bundle.BundleLinkComponent()
            .setRelation(Constants.LINK_SELF)
            .setUrl(components.toUriString()));
    to.addLink(
        new org.hl7.fhir.r4.model.Bundle.BundleLinkComponent()
            .setRelation(Constants.LINK_FIRST)
            .setUrl(buildUrl(null)));

    if (entries.size() == getPageSize() && entries.size() > 0) {
      org.hl7.fhir.r4.model.Patient lastPatient =
          (org.hl7.fhir.r4.model.Patient) entries.get(entries.size() - 1).getResource();
      Long lastPatientId = Long.parseLong(lastPatient.getId());
      to.addLink(
          new org.hl7.fhir.r4.model.Bundle.BundleLinkComponent()
              .setRelation(Constants.LINK_NEXT)
              .setUrl(buildUrl(lastPatientId)));
    }
  }

  public Long getCursor() {
    return cursor;
  }

  private Integer extractCountParam(UriComponents components) {
    String countText = components.getQueryParams().getFirst(Constants.PARAM_COUNT);
    if (countText != null) {
      try {
        return Integer.parseInt(countText);
      } catch (NumberFormatException ex) {
        throw new InvalidRequestException("Invalid _count parameter: " + countText);
      }
    }
    return null;
  }

  private Long extractCursorParam(UriComponents components) {
    String cursorText = components.getQueryParams().getFirst(PARAM_CURSOR);
    return cursorText != null && cursorText.length() > 0 ? Long.parseLong(cursorText) : null;
  }

  private String buildUrl(Long cursor) {
    MultiValueMap<String, String> params = components.getQueryParams();
    if (cursor != null) {
      params = new LinkedMultiValueMap<>(params);
      params.set(PARAM_CURSOR, String.valueOf(cursor));
    }
    return UriComponentsBuilder.newInstance()
        .uriComponents(components)
        .replaceQueryParams(params)
        .build()
        .toUriString();
  }

  /**
   * Get the value that should be passed as the max size for a query using paging. This value should
   * be at least as big as the page size to ensure a full page but include at least one additional
   * record as a way to determine whether another page will be needed. In practice this means
   * returning one more than the page size.
   *
   * @return the query max size
   */
  public int getQueryMaxSize() {
    return getPageSize() + 1;
  }
}
