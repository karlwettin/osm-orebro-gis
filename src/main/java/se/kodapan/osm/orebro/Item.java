package se.kodapan.osm.orebro;

import se.kodapan.geojson.GeoJSONObject;

/**
 * @author kalle@kodapan.se
 * @since 2014-10-25 18:13
 */
public class Item {

  private int layerId;
  private String title;
  private GeoJSONObject geom;
  private String type;
  private String city;
  private String town;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Item item = (Item) o;

    if (layerId != item.layerId) return false;
    if (city != null ? !city.equals(item.city) : item.city != null) return false;
    if (geom != null ? !geom.equals(item.geom) : item.geom != null) return false;
    if (title != null ? !title.equals(item.title) : item.title != null) return false;
    if (town != null ? !town.equals(item.town) : item.town != null) return false;
    if (type != null ? !type.equals(item.type) : item.type != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = layerId;
    result = 31 * result + (title != null ? title.hashCode() : 0);
    result = 31 * result + (geom != null ? geom.hashCode() : 0);
    result = 31 * result + (type != null ? type.hashCode() : 0);
    result = 31 * result + (city != null ? city.hashCode() : 0);
    result = 31 * result + (town != null ? town.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "Item{" +
        "layerId=" + layerId +
        ", title='" + title + '\'' +
        ", geom=" + geom +
        ", type='" + type + '\'' +
        ", city='" + city + '\'' +
        ", town='" + town + '\'' +
        '}';
  }

  public int getLayerId() {
    return layerId;
  }

  public void setLayerId(int layerId) {
    this.layerId = layerId;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public GeoJSONObject getGeom() {
    return geom;
  }

  public void setGeom(GeoJSONObject geom) {
    this.geom = geom;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getCity() {
    return city;
  }

  public void setCity(String city) {
    this.city = city;
  }

  public String getTown() {
    return town;
  }

  public void setTown(String town) {
    this.town = town;
  }
}
