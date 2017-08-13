package se.tedro.maven.plugin.reproto.github;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class GithubRelease {
  private final String tagName;

  @JsonCreator
  public GithubRelease(@JsonProperty("tag_name") final String tagName) {
    this.tagName = tagName;
  }
}
