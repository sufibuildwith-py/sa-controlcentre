package com.saproduction.command.navigator;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix="app.navigator")
public class NavigatorConfig {
  private boolean enabled;
  private boolean simulatorEnabled;
  private String gatewayUrl;
  private String organizationPublicId;
  private String serviceKey;
  public boolean isEnabled(){return enabled;} public void setEnabled(boolean v){enabled=v;}
  public boolean isSimulatorEnabled(){return simulatorEnabled;} public void setSimulatorEnabled(boolean v){simulatorEnabled=v;}
  public String getGatewayUrl(){return gatewayUrl;} public void setGatewayUrl(String v){gatewayUrl=v;}
  public String getOrganizationPublicId(){return organizationPublicId;} public void setOrganizationPublicId(String v){organizationPublicId=v;}
  public String getServiceKey(){return serviceKey;} public void setServiceKey(String v){serviceKey=v;}
}
