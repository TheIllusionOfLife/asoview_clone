package com.asoviewclone.commercecore.identity.model;

public enum TenantRole {
  OWNER(0),
  ADMIN(1),
  STAFF(2),
  ANALYST(3),
  VIEWER(4);

  private final int privilegeLevel;

  TenantRole(int privilegeLevel) {
    this.privilegeLevel = privilegeLevel;
  }

  public int getPrivilegeLevel() {
    return privilegeLevel;
  }
}
