package com.asoviewclone.commercecore.catalog.controller;

import com.asoviewclone.commercecore.identity.model.Venue;
import com.asoviewclone.commercecore.identity.repository.VenueRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 2 minimal implementation of {@code /v1/areas}. An "area" in the Asoview! marketplace maps
 * 1:1 to a {@link Venue} for now — the real area hierarchy (region → prefecture → city) is deferred
 * to a later phase once we have OpenSearch wired up.
 *
 * <p>Returning venue ids keeps the frontend contract forward-compatible: the same {@code area} id
 * that the client passes as {@code ?area=...} on {@code /v1/products} is a venue id today and can
 * become a synthetic area id later without breaking the client.
 */
@RestController
@RequestMapping("/v1/areas")
public class AreaController {

  private final VenueRepository venueRepository;

  public AreaController(VenueRepository venueRepository) {
    this.venueRepository = venueRepository;
  }

  @GetMapping
  public List<AreaResponse> listAreas() {
    return venueRepository.findAll().stream().map(AreaResponse::from).toList();
  }

  public record AreaResponse(UUID id, String name, String slug, String address) {
    static AreaResponse from(Venue v) {
      return new AreaResponse(v.getId(), v.getName(), toSlug(v.getName()), v.getAddress());
    }

    private static String toSlug(String name) {
      if (name == null) return "";
      return name.trim()
          .toLowerCase(java.util.Locale.ROOT)
          .replaceAll("[^\\p{Alnum}]+", "-")
          .replaceAll("(^-|-$)", "");
    }
  }
}
