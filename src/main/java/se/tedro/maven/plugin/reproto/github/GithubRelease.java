package se.tedro.maven.plugin.reproto.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.beans.ConstructorProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class GithubRelease {
  private final String tagName;

  @ConstructorProperties({"tag_name"})
  public GithubRelease(final String tagName) {
    this.tagName = tagName;
  }
}
