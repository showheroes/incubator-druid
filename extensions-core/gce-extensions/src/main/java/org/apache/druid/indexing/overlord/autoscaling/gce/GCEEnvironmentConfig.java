/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.druid.indexing.overlord.autoscaling.gce;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 */
public class GCEEnvironmentConfig
{
  private final String projectId;
  private final String applicationName;
  private final String zoneName;

  @JsonCreator
  public GCEEnvironmentConfig(
      @JsonProperty("projectId") String projectId,
      @JsonProperty("applicationName") String applicationName,
      @JsonProperty("zoneName") String zoneName
  )
  {
    this.projectId = projectId;
    this.applicationName = applicationName;
    this.zoneName = zoneName;
  }

  @JsonProperty
  public String getProjectId()
  {
    return projectId;
  }

  @JsonProperty
  public String getApplicationName()
  {
    return applicationName;
  }

  @JsonProperty
  public String getZoneName()
  {
    return zoneName;
  }

  @Override
  public String toString()
  {
    return "GCEEnvironmentConfig{" +
           "projectId='" + projectId + '\'' +
           ", applicationName='" + applicationName + '\'' +
           ", zoneName='" + zoneName + '\'' +
           '}';
  }

  @Override
  public boolean equals(Object o)
  {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    GCEEnvironmentConfig that = (GCEEnvironmentConfig) o;

    if (projectId != null ? !projectId.equals(that.projectId) : that.projectId != null) {
      return false;
    }
    if (applicationName != null ? !applicationName.equals(that.applicationName) : that.applicationName != null) {
      return false;
    }
    if (zoneName != null ? !zoneName.equals(that.zoneName) : that.zoneName != null) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode()
  {
    int result = projectId != null ? projectId.hashCode() : 0;
    result = 31 * result + (applicationName != null ? applicationName.hashCode() : 0);
    result = 31 * result + (zoneName != null ? zoneName.hashCode() : 0);
    return result;
  }
}
