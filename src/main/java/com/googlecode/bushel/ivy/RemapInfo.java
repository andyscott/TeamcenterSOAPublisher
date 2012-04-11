package com.googlecode.bushel.ivy;

public class RemapInfo {
  

  private String name;
  private String org;
  private String rev;
  
  
  
  public RemapInfo(String name, String org, String rev) {
    this.name = name;
    this.org = org;
    this.rev = rev;
  }
  public String getName() {
    return name;
  }
  public void setName(String name) {
    this.name = name;
  }
  public String getOrg() {
    return org;
  }
  public void setOrg(String org) {
    this.org = org;
  }
  public String getRev() {
    return rev;
  }
  public void setRev(String rev) {
    this.rev = rev;
  }
}
