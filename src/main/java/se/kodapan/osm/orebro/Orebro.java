package se.kodapan.osm.orebro;

import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.json.JSONArray;
import org.json.JSONTokener;
import se.kodapan.geojson.Point;
import se.kodapan.osm.domain.*;
import se.kodapan.osm.domain.root.PojoRoot;
import se.kodapan.osm.domain.root.indexed.IndexedRoot;
import se.kodapan.osm.domain.root.indexed.IndexedRootImpl;
import se.kodapan.osm.parser.xml.instantiated.InstantiatedOsmXmlParser;
import se.kodapan.osm.services.overpass.FileSystemCachedOverpass;
import se.kodapan.osm.xml.OsmXmlWriter;

import java.io.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author kalle@kodapan.se
 * @since 2014-10-25 17:23
 */
public class Orebro {

  public final static String serverURLPrefix = "http://data.karta.orebro.se/api/v1/";

  public final static File data = new File("data");

  public static boolean isGatunamnSuffix(String title) {
    title = title.toLowerCase();
    return (title.endsWith("gata")
        || title.endsWith("gatan")
        || title.endsWith("väg")
        || title.endsWith("vägen")
        || title.endsWith("gränd")
        || title.endsWith("gränden")
        || title.endsWith("rondell")
        || title.endsWith("rondellen")
        || title.endsWith("stig")
        || title.endsWith("stigen")
        || title.endsWith("leden"));
  }


  public static void main(String[] args) throws Exception {

    if (!data.exists()) {
      data.mkdirs();
    }

    new Orebro().run();

  }


  public void run() throws Exception {

    final OsmXmlWriter osmXmlWriter = new OsmXmlWriter(new OutputStreamWriter(new FileOutputStream(new File(data, "orebro-" + System.currentTimeMillis() + ".osm.xml")), "UTF8"));

    OsmObjectVisitor<Void> writeRecursedXml = new OsmObjectVisitor<Void>() {
      @Override
      public Void visit(Node node) {
        try {
          osmXmlWriter.write(node);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
        return null;
      }

      @Override
      public Void visit(Way way) {
        try {
          osmXmlWriter.write(way);
          for (Node node : way.getNodes()) {
            node.accept(this);
          }
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
        return null;
      }

      @Override
      public Void visit(Relation relation) {
        try {
          osmXmlWriter.write(relation);
          for (RelationMembership membership : relation.getMembers()) {
            membership.getObject().accept(this);
          }
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
        return null;
      }
    };


    FileSystemCachedOverpass overpass;

    PojoRoot root;
    IndexedRoot<Query> index;

    Map<Item, Node> houseNumbers = new HashMap<>();
    Map<Item, Node> streets = new HashMap<>();
    Map<Item, Node> addressLandsbygd = new HashMap<>();
    Map<Item, Node> addressPlaces = new HashMap<>();
    Set<String> streetNames = new HashSet<>();


    overpass = new FileSystemCachedOverpass(new File(data, "overpass"));
    overpass.setUserAgent(getClass().getName());
    overpass.open();
    try {


      //
      //  ladda in örebro som osm-data så vi kan leta upp kända och okända vägar, husnummer och uppgångar, etc.
      {
        String xml = overpass.execute("<osm-script>\n" +
            "  <union>\n" +
            "    <query type=\"relation\">\n" +
            "      <has-kv k=\"highway\"/>\n" +
            "      <bbox-query e=\"15.40626525878906\" n=\"59.371341958846685\" s=\"59.1850748575612\" w=\"15.038909912109375\"/>\n" +
            "    </query>\n" +
            "    <query type=\"way\">\n" +
            "      <has-kv k=\"highway\"/>\n" +
            "      <bbox-query e=\"15.40626525878906\" n=\"59.371341958846685\" s=\"59.1850748575612\" w=\"15.038909912109375\"/>\n" +
            "    </query>\n" +
            "    <query type=\"relation\">\n" +
            "      <has-kv k=\"addr:housenumber\"/>\n" +
            "      <bbox-query e=\"15.40626525878906\" n=\"59.371341958846685\" s=\"59.1850748575612\" w=\"15.038909912109375\"/>\n" +
            "    </query>\n" +
            "    <query type=\"way\">\n" +
            "      <has-kv k=\"addr:housenumber\"/>\n" +
            "      <bbox-query e=\"15.40626525878906\" n=\"59.371341958846685\" s=\"59.1850748575612\" w=\"15.038909912109375\"/>\n" +
            "    </query>\n" +
            "    <query type=\"node\">\n" +
            "      <has-kv k=\"addr:housenumber\"/>\n" +
            "      <bbox-query e=\"15.40626525878906\" n=\"59.371341958846685\" s=\"59.1850748575612\" w=\"15.038909912109375\"/>\n" +
            "    </query>\n" +
            "  </union>\n" +
            "  <union>\n" +
            "    <item/>\n" +
            "    <recurse type=\"down\"/>\n" +
            "  </union>\n" +
            "  <print mode=\"meta\"/>\n" +
            "</osm-script>\n");

        root = new PojoRoot();
        InstantiatedOsmXmlParser parser = InstantiatedOsmXmlParser.newInstance();
        parser.setRoot(root);

        parser.parse(xml);

        File path = new File(data, "index");
        boolean reconstruct = !path.exists();
        index = new IndexedRootImpl(root, path);
        index.open();
        if (reconstruct) {
          index.reconstruct(20);
        }
      }


      Search search = new Search();


      {
        int previousFoundHouseNumber = 0;
//        for (int houseNumber = 1; houseNumber - previousFoundHouseNumber < 25; houseNumber++) {
        for (int houseNumber = 1; houseNumber < 500; houseNumber++) {

          // sök efter husnummer och landsbygdsnamn
          {
            String textQuery = " " + String.valueOf(houseNumber);
            JSONArray response = search.search(new File(data, "search"), textQuery);
            for (int i = 0; i < response.length(); i++) {
              Item item = Search.unmarshallJSONItem(response.getJSONObject(i));
              if ("StreetAddress".equals(item.getType())
                  && item.getTitle().toLowerCase().endsWith(textQuery.toLowerCase())) {

                if ("Landsbygd".equals(item.getTown())) {

                  // todo sök efter name:, addr:place, addr:street, etc i området

                  Node streetAddress = new Node();
                  Point point = (Point) item.getGeom();
                  streetAddress.setLatitude(point.getLatitude());
                  streetAddress.setLongitude(point.getLongitude());
                  streetAddress.setTag("source", "data.karta.orebro.se");
                  streetAddress.setTag("source:license", "cc-by");
                  streetAddress.setTag("addr:place", item.getTitle());

                  // todo
                  streetAddress.setTag("addr:city", item.getCity());
                  if (!item.getCity().equals(item.getTown())) {
                    streetAddress.setTag("addr:town", item.getTown());
                  }

                  addressLandsbygd.put(item, streetAddress);

                } else {


                  Node streetAddress = new Node();
                  Point point = (Point) item.getGeom();
                  streetAddress.setLatitude(point.getLatitude());
                  streetAddress.setLongitude(point.getLongitude());
                  streetAddress.setTag("source", "data.karta.orebro.se");
                  streetAddress.setTag("source:license", "cc-by");
                  streetAddress.setTag("addr:housenumber", String.valueOf(houseNumber));
                  streetAddress.setTag("ref:se:husnummer", String.valueOf(houseNumber));
                  streetAddress.setTag("addr:street", item.getTitle().substring(0, item.getTitle().indexOf(textQuery)).trim());
                  streetNames.add(streetAddress.getTag("addr:street"));

                  // todo
                  streetAddress.setTag("addr:city", item.getCity());
                  if (!item.getCity().equals(item.getTown())) {
                    streetAddress.setTag("addr:town", item.getTown());
                  }

                  houseNumbers.put(item, streetAddress);


                  // sök efter husnumret i området, lägg till existerande i xml

                  BooleanQuery bq = new BooleanQuery();

                  BooleanQuery classQuery = new BooleanQuery();

                  classQuery.add(index.getQueryFactories().nodeRadialEnvelopeQueryFactory()
                      .setKilometerRadius(0.5)
                      .setLongitude(((Point) item.getGeom()).getLongitude())
                      .setLatitude(((Point) item.getGeom()).getLatitude())
                      .build()
                      , BooleanClause.Occur.SHOULD);

                  classQuery.add(index.getQueryFactories().wayRadialEnvelopeQueryFactory()
                      .setKilometerRadius(0.5)
                      .setLongitude(((Point) item.getGeom()).getLongitude())
                      .setLatitude(((Point) item.getGeom()).getLatitude())
                      .build()
                      , BooleanClause.Occur.SHOULD);

                  bq.add(classQuery
                      , BooleanClause.Occur.MUST);

                  bq.add(index.getQueryFactories().containsTagKeyAndValueQueryFactory()
                      .setKey("addr:street")
                      .setValue(streetAddress.getTag("addr:street"))
                      .build(), BooleanClause.Occur.MUST);

                  bq.add(index.getQueryFactories().containsTagKeyAndValueQueryFactory()
                      .setKey("addr:housenumber")
                      .setValue(streetAddress.getTag("addr:housenumber"))
                      .build(), BooleanClause.Occur.MUST);

                  Set<OsmObject> hits = index.search(bq).keySet();

                  if (!hits.isEmpty()) {
                    streetAddress.setTag("note", "duplicate");

                    // todo create duplicate-relation with hits + item?

                    for (OsmObject hit : hits) {
                      hit.accept(writeRecursedXml);
                    }
                  }

                }
                previousFoundHouseNumber = houseNumber;
              }
            }
          }


          // sök efter uppgångar på husnummer
          {
            for (char houseDoor : "ABCDEFGHIJKLMNOPQRSTUVQXYZÅÄÖ".toCharArray()) {
              String textQuery = " " + String.valueOf(houseNumber) + String.valueOf(houseDoor);
              JSONArray response = search.search(new File(data, "search"), textQuery);
              for (int i = 0; i < response.length(); i++) {
                Item item = Search.unmarshallJSONItem(response.getJSONObject(i));
                if ("StreetAddress".equals(item.getType())
                    && item.getTitle().toLowerCase().endsWith(textQuery.toLowerCase())) {

                  Node streetAddress = new Node();
                  Point point = (Point) item.getGeom();
                  streetAddress.setLatitude(point.getLatitude());
                  streetAddress.setLongitude(point.getLongitude());
                  streetAddress.setTag("source", "data.karta.orebro.se");
                  streetAddress.setTag("source:license", "cc-by");
                  streetAddress.setTag("addr:housenumber", String.valueOf(houseNumber) + String.valueOf(houseDoor));
                  streetAddress.setTag("ref:se:husnummer", String.valueOf(houseNumber));
                  streetAddress.setTag("ref:se:uppgång", String.valueOf(houseDoor));
                  streetAddress.setTag("addr:street", item.getTitle().substring(0, item.getTitle().indexOf(textQuery)).trim());
                  streetNames.add(streetAddress.getTag("addr:street"));

                  // todo
                  streetAddress.setTag("addr:city", item.getCity());
                  if (!item.getCity().equals(item.getTown())) {
                    streetAddress.setTag("addr:town", item.getTown());
                  }

                  houseNumbers.put(item, streetAddress);

                  // sök efter husnumret i området, lägg till existerande i xml

                  BooleanQuery bq = new BooleanQuery();

                  BooleanQuery classQuery = new BooleanQuery();

                  classQuery.add(index.getQueryFactories().nodeRadialEnvelopeQueryFactory()
                      .setKilometerRadius(0.5)
                      .setLongitude(((Point) item.getGeom()).getLongitude())
                      .setLatitude(((Point) item.getGeom()).getLatitude())
                      .build()
                      , BooleanClause.Occur.SHOULD);

                  classQuery.add(index.getQueryFactories().wayRadialEnvelopeQueryFactory()
                      .setKilometerRadius(0.5)
                      .setLongitude(((Point) item.getGeom()).getLongitude())
                      .setLatitude(((Point) item.getGeom()).getLatitude())
                      .build()
                      , BooleanClause.Occur.SHOULD);

                  bq.add(classQuery
                      , BooleanClause.Occur.MUST);

                  bq.add(index.getQueryFactories().containsTagKeyAndValueQueryFactory()
                      .setKey("addr:street")
                      .setValue(streetAddress.getTag("addr:street"))
                      .build(), BooleanClause.Occur.MUST);

                  bq.add(index.getQueryFactories().containsTagKeyAndValueQueryFactory()
                      .setKey("addr:housenumber")
                      .setValue(streetAddress.getTag("addr:housenumber"))
                      .build(), BooleanClause.Occur.MUST);

                  Set<OsmObject> hits = index.search(bq).keySet();

                  if (!hits.isEmpty()) {
                    streetAddress.setTag("note", "duplicate");

                    // todo create duplicate-relation with hits + item?

                    for (OsmObject hit : hits) {
                      hit.accept(writeRecursedXml);
                    }
                  }


                  previousFoundHouseNumber = houseNumber;
                }
              }
            }
          }
        }
      }


      // sök efter gator och platser
      {

        for (char c : "abcdefghijklmnopqrstuvwxyzåäö".toCharArray()) {
          JSONArray response = search.search(new File(data, "search"), String.valueOf(c));
          for (int i = 0; i < response.length(); i++) {
            if ("StreetAddress".equals(response.getJSONObject(i).get("type"))) {
              Item item = Search.unmarshallJSONItem(response.getJSONObject(i));

              if (streetNames.contains(item.getTitle())
                  || isGatunamnSuffix(item.getTitle())) {

                Node street = new Node();
                Point point = (Point) item.getGeom();
                street.setLatitude(point.getLatitude());
                street.setLongitude(point.getLongitude());
                street.setTag("source", "data.karta.orebro.se");
                street.setTag("source:license", "cc-by");
                street.setTag("highway", "road");
                street.setTag("name", item.getTitle());

                // todo
                street.setTag("addr:city", item.getCity());
                if (!item.getCity().equals(item.getTown())) {
                  street.setTag("addr:town", item.getTown());
                }


                streets.put(item, street);

                // sök efter gatan i området, lägg till existerande i xml

                BooleanQuery bq = new BooleanQuery();

                BooleanQuery classQuery = new BooleanQuery();

                classQuery.add(index.getQueryFactories().nodeRadialEnvelopeQueryFactory()
                    .setKilometerRadius(1)
                    .setLongitude(((Point) item.getGeom()).getLongitude())
                    .setLatitude(((Point) item.getGeom()).getLatitude())
                    .build()
                    , BooleanClause.Occur.SHOULD);

                //          classQuery.add(index.getQueryFactories().wayRadialEnvelopeQueryFactory()
                //              .setKilometerRadius(0.5)
                //              .setLongitude(houseNumber.getValue().getLongitude())
                //              .setLatitude(houseNumber.getValue().getLatitude())
                //              .build()
                //              , BooleanClause.Occur.SHOULD);

                bq.add(classQuery
                    , BooleanClause.Occur.MUST);

                bq.add(index.getQueryFactories().containsTagKeyAndValueQueryFactory()
                    .setKey("addr:street")
                    .setValue(street.getTag("name"))
                    .build(), BooleanClause.Occur.MUST);

                bq.add(index.getQueryFactories().containsTagKeyQueryFactory()
                    .setKey("highway")
                    .build(), BooleanClause.Occur.MUST);

                Set<OsmObject> hits = index.search(bq).keySet();

                if (!hits.isEmpty()) {
                  street.setTag("note", "duplicate");

                  // todo create duplicate-relation with hits + item?

                  for (OsmObject hit : hits) {
                    hit.accept(writeRecursedXml);
                  }
                }


              } else {

                // addr:place

                Node place = new Node();
                Point point = (Point) item.getGeom();
                place.setLatitude(point.getLatitude());
                place.setLongitude(point.getLongitude());
                place.setTag("source", "data.karta.orebro.se");
                place.setTag("source:license", "cc-by");
                place.setTag("addr:place", item.getTitle());

                // todo
                place.setTag("addr:city", item.getCity());
                if (!item.getCity().equals(item.getTown())) {
                  place.setTag("addr:town", item.getTown());
                }


                addressPlaces.put(item, place);

                // sök efter platsen i området, lägg till existerande i xml

                BooleanQuery bq = new BooleanQuery();

                BooleanQuery classQuery = new BooleanQuery();

                classQuery.add(index.getQueryFactories().nodeRadialEnvelopeQueryFactory()
                    .setKilometerRadius(5)
                    .setLongitude(((Point) item.getGeom()).getLongitude())
                    .setLatitude(((Point) item.getGeom()).getLatitude())
                    .build()
                    , BooleanClause.Occur.SHOULD);

                          classQuery.add(index.getQueryFactories().wayRadialEnvelopeQueryFactory()
                              .setKilometerRadius(0.5)
                              .setLongitude(((Point) item.getGeom()).getLongitude())
                              .setLatitude(((Point) item.getGeom()).getLatitude())
                              .build()
                              , BooleanClause.Occur.SHOULD);

                bq.add(classQuery
                    , BooleanClause.Occur.MUST);


                BooleanQuery nameQuery = new BooleanQuery();

                bq.add(index.getQueryFactories().containsTagKeyAndValueQueryFactory()
                    .setKey("addr:place")
                    .setValue(place.getTag("addr:place"))
                    .build(), BooleanClause.Occur.SHOULD);

                bq.add(index.getQueryFactories().containsTagKeyAndValueQueryFactory()
                    .setKey("name")
                    .setValue(place.getTag("addr:place"))
                    .build(), BooleanClause.Occur.SHOULD);

                bq.add(nameQuery
                    , BooleanClause.Occur.MUST);

                Set<OsmObject> hits = index.search(bq).keySet();

                if (!hits.isEmpty()) {
                  place.setTag("note", "duplicate");

                  // todo create duplicate-relation with hits + item?

                  for (OsmObject hit : hits) {
                    hit.accept(writeRecursedXml);
                  }
                }


              }


            }
          }
        }
      }


    } finally {
      overpass.close();
    }

    int nodeId = -1;
    for (Node node : streets.values()) {
      node.setId(nodeId--);
      osmXmlWriter.write(node);
    }
    for (Node node : houseNumbers.values()) {
      node.setId(nodeId--);
      osmXmlWriter.write(node);
    }
    for (Node node : addressLandsbygd.values()) {
      node.setId(nodeId--);
      osmXmlWriter.write(node);
    }
    for (Node node : addressPlaces.values()) {
      node.setId(nodeId--);
      osmXmlWriter.write(node);
    }

    osmXmlWriter.close();

  }


  public static void extractTypes() throws Exception {

    Set<String> types = new HashSet<>();

    for (File file : data.listFiles(new FileFilter() {
      @Override
      public boolean accept(File pathname) {
        return pathname.getName().endsWith(".json");
      }
    })) {

      JSONArray response = new JSONArray(new JSONTokener(new InputStreamReader(new FileInputStream(file), "UTF8")));
      for (int i = 0; i < response.length(); i++) {
        types.add(Search.unmarshallJSONItem(response.getJSONObject(i)).getType());
      }
    }

    System.out.println(types);

  }


}
