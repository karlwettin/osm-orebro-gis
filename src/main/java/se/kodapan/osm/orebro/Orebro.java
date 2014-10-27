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
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

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

    final OsmXmlWriter geoObjectWriter = new OsmXmlWriter(new OutputStreamWriter(new FileOutputStream(new File(data, "geo-objects.osm.xml")), "UTF8")) {
      private AtomicLong id = new AtomicLong(-1);

      @Override
      public void write(Node node) throws IOException {
        node.setId(id.getAndDecrement());
        super.write(node);
      }
    };

    final OsmXmlWriter houseNumberWriter = new OsmXmlWriter(new OutputStreamWriter(new FileOutputStream(new File(data, "house-numbers.osm.xml")), "UTF8")) {
      private AtomicLong id = new AtomicLong(-1);

      @Override
      public void write(Node node) throws IOException {
        node.setId(id.getAndDecrement());
        super.write(node);
      }
    };

    final OsmXmlWriter streetWriter = new OsmXmlWriter(new OutputStreamWriter(new FileOutputStream(new File(data, "streets.osm.xml")), "UTF8")) {
      private AtomicLong id = new AtomicLong(-1);

      @Override
      public void write(Node node) throws IOException {
        node.setId(id.getAndDecrement());
        super.write(node);
      }
    };

    final OsmXmlWriter placesLandsbygdWriter = new OsmXmlWriter(new OutputStreamWriter(new FileOutputStream(new File(data, "places-landsbygd.osm.xml")), "UTF8")) {
      private AtomicLong id = new AtomicLong(-1);

      @Override
      public void write(Node node) throws IOException {
        node.setId(id.getAndDecrement());
        super.write(node);
      }
    };

    final OsmXmlWriter uncertainWriter = new OsmXmlWriter(new OutputStreamWriter(new FileOutputStream(new File(data, "uncertain.osm.xml")), "UTF8")) {
      private AtomicLong id = new AtomicLong(-1);

      @Override
      public void write(Node node) throws IOException {
        node.setId(id.getAndDecrement());
        super.write(node);
      }
    };
//

    final OsmXmlWriter orebroDuplicatesWriter = new OsmXmlWriter(new OutputStreamWriter(new FileOutputStream(new File(data, "duplicates.osm.xml")), "UTF8")) {
      private AtomicLong id = new AtomicLong(-1);

      @Override
      public void write(Node node) throws IOException {
        node.setId(id.getAndDecrement());
        super.write(node);
      }
    };

    final OsmXmlWriter osmDuplicatesWriter = new OsmXmlWriter(new OutputStreamWriter(new FileOutputStream(new File(data, "osm-duplicates.osm.xml")), "UTF8"));


    OsmObjectVisitor<Void> writeToOsmDuplicates = new OsmObjectVisitor<Void>() {
      private Set<OsmObject> seen = new HashSet<>();

      @Override
      public Void visit(Node node) {

        if (seen.add(node)) {
          try {
            osmDuplicatesWriter.write(node);
            osmDuplicatesWriter.flush();
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        }
        return null;
      }

      @Override
      public Void visit(Way way) {
        if (seen.add(way)) {
          try {
            osmDuplicatesWriter.write(way);
            for (Node node : way.getNodes()) {
              node.accept(this);
            }
            osmDuplicatesWriter.flush();
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        }
        return null;
      }

      @Override
      public Void visit(Relation relation) {
        if (seen.add(relation)) {
          try {
            osmDuplicatesWriter.write(relation);
            for (RelationMembership membership : relation.getMembers()) {
              membership.getObject().accept(this);
            }
            osmDuplicatesWriter.flush();
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        }
        return null;
      }
    };


    FileSystemCachedOverpass overpass;

    PojoRoot root;
    IndexedRoot<Query> index;


    overpass = new FileSystemCachedOverpass(new File(data, "overpass"));
    overpass.setUserAgent(getClass().getName());
    overpass.open();
    try {


      //
      System.out.println("ladda in örebro som osm-data så vi kan leta upp kända och okända vägar, husnummer och uppgångar, etc.");
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

      Set<String> streetNames = new HashSet<>();
      Set<String> placeNames = new HashSet<>();


      System.out.println("sök efter husnummer och landsbygdsnamn");
      {
        int previousFoundHouseNumber = 0;
//        for (int houseNumber = 1; houseNumber - previousFoundHouseNumber < 25; houseNumber++) {
        for (int houseNumber = 1; houseNumber < 10000 && houseNumber - previousFoundHouseNumber < 1500; houseNumber++) {

          System.out.println(houseNumber);

          // sök efter husnummer och landsbygdsnamn
          {
            String textQuery = " " + String.valueOf(houseNumber);
            JSONArray response = search.search(new File(data, "search"), textQuery);
            for (int i = 0; i < response.length(); i++) {
              Item item = Search.unmarshallJSONItem(response.getJSONObject(i));
              if ("StreetAddress".equals(item.getType())
                  && item.getTitle().toLowerCase().endsWith(textQuery.toLowerCase())) {

                if ("Landsbygd".equals(item.getTown())) {


                  Node landsbygdsadress = new Node();
                  Point point = (Point) item.getGeom();
                  landsbygdsadress.setLatitude(point.getLatitude());
                  landsbygdsadress.setLongitude(point.getLongitude());
                  addSource(landsbygdsadress);
                  landsbygdsadress.setTag("addr:place", item.getTitle().substring(0, item.getTitle().indexOf(textQuery)).trim());
                  landsbygdsadress.setTag("addr:housenumber", String.valueOf(houseNumber));

                  placeNames.add(landsbygdsadress.getTag("addr:place"));

                  addCity(item, landsbygdsadress);


                  // todo sök efter name:, addr:place, addr:street, etc i området

                  houseNumberWriter.write(landsbygdsadress);


                } else {


                  Node streetAddress = new Node();
                  Point point = (Point) item.getGeom();
                  streetAddress.setLatitude(point.getLatitude());
                  streetAddress.setLongitude(point.getLongitude());
                  addSource(streetAddress);
                  streetAddress.setTag("addr:housenumber", String.valueOf(houseNumber));
                  streetAddress.setTag("addr:street", item.getTitle().substring(0, item.getTitle().indexOf(textQuery)).trim());
                  streetNames.add(streetAddress.getTag("addr:street"));

                  addCity(item, streetAddress);


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
                      hit.accept(writeToOsmDuplicates);
                    }

                    orebroDuplicatesWriter.write(streetAddress);

                  } else {

                    houseNumberWriter.write(streetAddress);

                  }

                }
                previousFoundHouseNumber = houseNumber;
              }
            }
          }
        }
      }


      System.out.println("sök efter uppgångar på husnummer");//
      {
        int previousFoundHouseNumber = 0;
        for (int houseNumber = 1; houseNumber - previousFoundHouseNumber < 25; houseNumber++) {
          System.out.println(houseNumber);
//            for (int houseNumber = 1; houseNumber < 500; houseNumber++) {

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
                addSource(streetAddress);
                streetAddress.setTag("addr:housenumber", String.valueOf(houseNumber) + String.valueOf(houseDoor));
                streetAddress.setTag("ref:se:husnummer", String.valueOf(houseNumber));
                streetAddress.setTag("ref:se:uppgång", String.valueOf(houseDoor));
                streetAddress.setTag("addr:street", item.getTitle().substring(0, item.getTitle().indexOf(textQuery)).trim());
                streetNames.add(streetAddress.getTag("addr:street"));

                addCity(item, streetAddress);

                //houseNumbers.put(item, streetAddress);

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
                    hit.accept(writeToOsmDuplicates);
                  }

                  orebroDuplicatesWriter.write(streetAddress);

                } else {

                  houseNumberWriter.write(streetAddress);


                }


                previousFoundHouseNumber = houseNumber;
              }
            }
          }
        }
      }


      System.out.println("sök efter gator och platser");
      {

        for (char c : "abcdefghijklmnopqrstuvwxyzåäö".toCharArray()) {

          System.out.println(c);

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
                addSource(street);
                street.setTag("highway", "road");
                street.setTag("name", item.getTitle());

                addCity(item, street);


                // sök efter gatan i området, lägg till existerande i xml

                BooleanQuery bq = new BooleanQuery();

                BooleanQuery classQuery = new BooleanQuery();

                classQuery.add(index.getQueryFactories().nodeRadialEnvelopeQueryFactory()
                    .setKilometerRadius(1)
                    .setLongitude(((Point) item.getGeom()).getLongitude())
                    .setLatitude(((Point) item.getGeom()).getLatitude())
                    .build()
                    , BooleanClause.Occur.SHOULD);

                classQuery.add(index.getQueryFactories().wayRadialEnvelopeQueryFactory()
                    .setKilometerRadius(1)
                    .setLongitude(((Point) item.getGeom()).getLongitude())
                    .setLatitude(((Point) item.getGeom()).getLatitude())
                    .build()
                    , BooleanClause.Occur.SHOULD);

                bq.add(classQuery
                    , BooleanClause.Occur.MUST);

                bq.add(index.getQueryFactories().containsTagKeyQueryFactory()
                    .setKey("highway")
                    .build(), BooleanClause.Occur.MUST);

                bq.add(index.getQueryFactories().containsTagKeyAndValueQueryFactory()
                    .setKey("name")
                    .setValue(street.getTag("name"))
                    .build(), BooleanClause.Occur.MUST);


                Set<OsmObject> hits = index.search(bq).keySet();

                if (!hits.isEmpty()) {
                  street.setTag("note", "duplicate");

                  // todo create duplicate-relation with hits + item?

                  for (OsmObject hit : hits) {
                    hit.accept(writeToOsmDuplicates);
                  }

                  orebroDuplicatesWriter.write(street);

                } else {

                  streetWriter.write(street);

                }

              } else if (placeNames.contains(item.getTitle())) {

                Node landsbygsPlace = new Node();
                Point point = (Point) item.getGeom();
                landsbygsPlace.setLatitude(point.getLatitude());
                landsbygsPlace.setLongitude(point.getLongitude());
                addSource(landsbygsPlace);
                landsbygsPlace.setTag("place", "hamlet");
                landsbygsPlace.setTag("name", item.getTitle());
                addCity(item, landsbygsPlace);


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
                    .setKilometerRadius(5)
                    .setLongitude(((Point) item.getGeom()).getLongitude())
                    .setLatitude(((Point) item.getGeom()).getLatitude())
                    .build()
                    , BooleanClause.Occur.SHOULD);

                bq.add(classQuery
                    , BooleanClause.Occur.MUST);


                BooleanQuery nameQuery = new BooleanQuery();

                bq.add(index.getQueryFactories().containsTagKeyAndValueQueryFactory()
                    .setKey("name")
                    .setValue(landsbygsPlace.getTag("name"))
                    .build(), BooleanClause.Occur.SHOULD);

                bq.add(nameQuery
                    , BooleanClause.Occur.MUST);

                Set<OsmObject> hits = index.search(bq).keySet();


                if (!hits.isEmpty()) {
                  landsbygsPlace.setTag("note", "duplicate");
                  // todo create duplicate-relation with hits + item?
                  for (OsmObject hit : hits) {
                    hit.accept(writeToOsmDuplicates);
                  }
                  orebroDuplicatesWriter.write(landsbygsPlace);

                } else {
                  placesLandsbygdWriter.write(landsbygsPlace);
                }


              } else {

                // uncertain but still interesting

                Node uncertain = new Node();
                Point point = (Point) item.getGeom();
                uncertain.setLatitude(point.getLatitude());
                uncertain.setLongitude(point.getLongitude());
                addSource(uncertain);
                uncertain.setTag("name", item.getTitle());
                addCity(item, uncertain);


                // sök efter platsen i området, lägg till existerande i xml

                BooleanQuery bq = new BooleanQuery();

                BooleanQuery classQuery = new BooleanQuery();

                classQuery.add(index.getQueryFactories().nodeRadialEnvelopeQueryFactory()
                    .setKilometerRadius(1)
                    .setLongitude(((Point) item.getGeom()).getLongitude())
                    .setLatitude(((Point) item.getGeom()).getLatitude())
                    .build()
                    , BooleanClause.Occur.SHOULD);

                classQuery.add(index.getQueryFactories().wayRadialEnvelopeQueryFactory()
                    .setKilometerRadius(1)
                    .setLongitude(((Point) item.getGeom()).getLongitude())
                    .setLatitude(((Point) item.getGeom()).getLatitude())
                    .build()
                    , BooleanClause.Occur.SHOULD);

                bq.add(classQuery
                    , BooleanClause.Occur.MUST);


                BooleanQuery nameQuery = new BooleanQuery();

                bq.add(index.getQueryFactories().containsTagKeyAndValueQueryFactory()
                    .setKey("addr:place")
                    .setValue(uncertain.getTag("name"))
                    .build(), BooleanClause.Occur.SHOULD);

                bq.add(index.getQueryFactories().containsTagKeyAndValueQueryFactory()
                    .setKey("name")
                    .setValue(uncertain.getTag("name"))
                    .build(), BooleanClause.Occur.SHOULD);

                bq.add(nameQuery
                    , BooleanClause.Occur.MUST);

                Set<OsmObject> hits = index.search(bq).keySet();


                if (!hits.isEmpty()) {
                  uncertain.setTag("note", "duplicate");
                  // todo create duplicate-relation with hits + item?
                  for (OsmObject hit : hits) {
                    hit.accept(writeToOsmDuplicates);
                  }
                  orebroDuplicatesWriter.write(uncertain);

                } else {
                  uncertainWriter.write(uncertain);
                }

              }


            }
          }
        }
      }


      System.out.println("leta upp alla GeoObject");
      {
        Set<Item> seen = new HashSet<>();
        for (File file : new File(data, "search").listFiles(new FileFilter() {
          @Override
          public boolean accept(File pathname) {
            return pathname.getName().endsWith(".json");
          }
        })) {
          JSONArray response = new JSONArray(new JSONTokener(new InputStreamReader(new FileInputStream(file), "UTF8")));
          for (int i = 0; i < response.length(); i++) {
            Item item = Search.unmarshallJSONItem(response.getJSONObject(i));
            if ("GeoObject".equals(item.getType())) {

              if (seen.add(item)) {

                Node geoObject = new Node();

                Point point = (Point) item.getGeom();
                geoObject.setLatitude(point.getLatitude());
                geoObject.setLongitude(point.getLongitude());
                addSource(geoObject);
                geoObject.setTag("name", item.getTitle());
                addCity(item, geoObject);

                // sök efter platsen i området, lägg till existerande i xml

                BooleanQuery bq = new BooleanQuery();

                BooleanQuery classQuery = new BooleanQuery();

                classQuery.add(index.getQueryFactories().nodeRadialEnvelopeQueryFactory()
                    .setKilometerRadius(1)
                    .setLongitude(((Point) item.getGeom()).getLongitude())
                    .setLatitude(((Point) item.getGeom()).getLatitude())
                    .build()
                    , BooleanClause.Occur.SHOULD);

                classQuery.add(index.getQueryFactories().wayRadialEnvelopeQueryFactory()
                    .setKilometerRadius(1)
                    .setLongitude(((Point) item.getGeom()).getLongitude())
                    .setLatitude(((Point) item.getGeom()).getLatitude())
                    .build()
                    , BooleanClause.Occur.SHOULD);

                bq.add(classQuery
                    , BooleanClause.Occur.MUST);


                BooleanQuery nameQuery = new BooleanQuery();

                bq.add(index.getQueryFactories().containsTagKeyAndValueQueryFactory()
                    .setKey("addr:place")
                    .setValue(geoObject.getTag("name"))
                    .build(), BooleanClause.Occur.SHOULD);

                bq.add(index.getQueryFactories().containsTagKeyAndValueQueryFactory()
                    .setKey("name")
                    .setValue(geoObject.getTag("name"))
                    .build(), BooleanClause.Occur.SHOULD);

                bq.add(nameQuery
                    , BooleanClause.Occur.MUST);

                Set<OsmObject> hits = index.search(bq).keySet();


                if (!hits.isEmpty()) {
                  geoObject.setTag("note", "duplicate");
                  // todo create duplicate-relation with hits + item?
                  for (OsmObject hit : hits) {
                    hit.accept(writeToOsmDuplicates);
                  }
                  orebroDuplicatesWriter.write(geoObject);

                } else {
                  geoObjectWriter.write(geoObject);
                }

              }

            }
          }
        }
      }


    } finally {
      overpass.close();
    }


    geoObjectWriter.close();
    houseNumberWriter.close();
    streetWriter.close();
    placesLandsbygdWriter.close();
    uncertainWriter.close();
    osmDuplicatesWriter.close();
    orebroDuplicatesWriter.close();

  }

  private void addSource(Node uncertain) {
    uncertain.setTag("source", "data.karta.orebro.se");
    uncertain.setTag("source:license", "cc-by");
  }

  private void addCity(Item item, Node place) {

    if (item.getCity() != null) {
      place.setTag("addr:city", item.getCity());
    }
    if (item.getTown() != null && !item.getTown().equals(item.getCity())) {
      if (!"Landsbygd".equals(item.getTown())) {
        if (place.getTag("addr:place") == null) {
          place.setTag("addr:place", item.getTown());
        }
      }
    }

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
