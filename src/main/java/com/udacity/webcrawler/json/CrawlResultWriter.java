package com.udacity.webcrawler.json;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.Objects;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Writes a {@link CrawlResult} to JSON.
 */
public final class CrawlResultWriter {

  private final CrawlResult result;

  /**
   * Creates a {@link CrawlResultWriter} for the specified result.
   */
  public CrawlResultWriter(CrawlResult result) {
    this.result = Objects.requireNonNull(result);
  }

  /**
   * Writes the result as JSON to the given writer.
   */
  public void write(Writer writer) {
    Objects.requireNonNull(writer);

    ObjectMapper mapper = new ObjectMapper();

    mapper.enable(SerializationFeature.INDENT_OUTPUT);

    mapper.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);

    try {
      mapper.writeValue(writer, result);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}