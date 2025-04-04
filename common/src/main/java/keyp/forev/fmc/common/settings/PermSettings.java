package keyp.forev.fmc.common.settings;

public enum PermSettings {
  NEW_FMC_USER("group.new-fmc-user"),
  SUB_ADMIN("group.sub-admin"),
  SUPER_ADMIN("group.super-admin"),
  RETRY("fmc.proxy.retry"),
  HUB("fmc.proxy.hub"),
  CEND("fmc.proxy.cend"),
  PERM("fmc.proxy.perm"),
  TPR("fmc.tpr"),
  SILENT("fmc.proxy.silent"),
  TELEPORT_REGISTER_POINT("fmc.registerpoint"),
  IMAGEMAP_REGISTER_MAP("fmc.registermap")
  ;

  private final String value;

  PermSettings(String key) {
    this.value = key;
  }

  public String get() {
    return this.value;
  }
}
