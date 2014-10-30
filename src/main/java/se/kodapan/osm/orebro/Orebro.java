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
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author kalle@kodapan.se
 * @since 2014-10-25 17:23
 */
public class Orebro {

  public final static String serverURLPrefix = "http://data.karta.orebro.se/api/v1/";

  public final static File data = new File("data");

  public static void main(String[] args) throws Exception {

    if (!data.exists()) {
      data.mkdirs();
    }

    new Orebro().run();

  }


  public void run() throws Exception {


    File path = new File(data, "osm.xml");
    path.mkdirs();


    final OsmXmlWriter orebroDuplicatesWriter = new OsmXmlWriter(new OutputStreamWriter(new FileOutputStream(new File(path, "dubletter-orebro.osm.xml")), "UTF8")) {
      private AtomicLong id = new AtomicLong(-1);

      @Override
      public void write(Node node) throws IOException {
        node.setId(id.getAndDecrement());
        super.write(node);
      }
    };


    final OsmXmlWriter osmDuplicatesWriter = new OsmXmlWriter(new OutputStreamWriter(new FileOutputStream(new File(path, "dubletter-osm.osm.xml")), "UTF8"));


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
            // highway
            "    <query type=\"relation\">\n" +
            "      <has-kv k=\"highway\"/>\n" +
            "      <bbox-query e=\"15.40626525878906\" n=\"59.371341958846685\" s=\"59.1850748575612\" w=\"15.038909912109375\"/>\n" +
            "    </query>\n" +
            "    <query type=\"way\">\n" +
            "      <has-kv k=\"highway\"/>\n" +
            "      <bbox-query e=\"15.40626525878906\" n=\"59.371341958846685\" s=\"59.1850748575612\" w=\"15.038909912109375\"/>\n" +
            "    </query>\n" +
            // addr:housenumber
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
            // name=*
            "    <query type=\"way\">\n" +
            "      <has-kv k=\"name\"/>\n" +
            "      <bbox-query e=\"15.40626525878906\" n=\"59.371341958846685\" s=\"59.1850748575612\" w=\"15.038909912109375\"/>\n" +
            "    </query>\n" +
            "    <query type=\"node\">\n" +
            "      <has-kv k=\"name\"/>\n" +
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

        File indexPath = new File(data, "index");
        boolean reconstruct = !indexPath.exists();
        index = new IndexedRootImpl(root, indexPath);
        index.open();
        if (reconstruct) {
          index.reconstruct(20);
        }
      }


      Search search = new Search();


      System.out.println("sök efter husnummer och landsbygdsnamn");
      {
        int previousFoundHouseNumber = 0;
        for (int houseNumber = 1; houseNumber < 10000 && houseNumber - previousFoundHouseNumber < 1500; houseNumber++) {
          // sök efter husnummer och landsbygdsnamn
          {
            String textQuery = " " + String.valueOf(houseNumber);
            JSONArray response = search.search(new File(data, "search"), textQuery);
            for (int i = 0; i < response.length(); i++) {
              Item item = Search.unmarshallJSONItem(response.getJSONObject(i));
              if ("StreetAddress".equals(item.getType())
                  && item.getTitle().toLowerCase().endsWith(textQuery.toLowerCase())) {
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
          for (char houseDoor : "ABCDEFGHIJKLMNOPQRSTUVQXYZÅÄÖ".toCharArray()) {
            String textQuery = " " + String.valueOf(houseNumber) + String.valueOf(houseDoor);
            JSONArray response = search.search(new File(data, "search"), textQuery);
            for (int i = 0; i < response.length(); i++) {
              Item item = Search.unmarshallJSONItem(response.getJSONObject(i));
              if ("StreetAddress".equals(item.getType())
                  && item.getTitle().toLowerCase().endsWith(textQuery.toLowerCase())) {
                previousFoundHouseNumber = houseNumber;
              }
            }
          }
        }
      }


      System.out.println("sök efter gator och platser");
      {

        for (char c : "abcdefghijklmnopqrstuvwxyzåäö".toCharArray()) {
          JSONArray response = search.search(new File(data, "search"), String.valueOf(c));
        }
      }


//      Pattern landsbygdAddressPattern = Pattern.compile("(.+) ([1-9][0-9]*)");
      Pattern streetAddressPattern = Pattern.compile("(.+) (([1-9][0-9]*)([A-Z]?))");

      System.out.println("leta upp alla objekt per layerid");
      {
        Map<Integer, Set<Item>> itemsByLayer = new HashMap<>();

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
            if (seen.add(item)) {

              Set<Item> items = itemsByLayer.get(item.getLayerId());
              if (items == null) {
                items = new HashSet<>();
                itemsByLayer.put(item.getLayerId(), items);
              }
              items.add(item);
            }
          }

        }

        System.currentTimeMillis();
        long id = -1;
        for (Map.Entry<Integer, Set<Item>> entries : itemsByLayer.entrySet()) {


          String name;
          if (entries.getKey() == 217) {
            name = "flextrafikens hallplatser";
          } else if (entries.getKey() == 219) {
            name = "bussparkering";
          } else if (entries.getKey() == 24) {
            name = "parkering";
          } else if (entries.getKey() == 258) {
            name = "handikappsparkering";
          } else if (entries.getKey() == 9) {
            name = "atervinningscentral";
          } else if (entries.getKey() == 29) {
            name = "atervinningsstation";
          } else if (entries.getKey() == 17) {
            name = "atervinning ris och kompost";
          } else if (entries.getKey() == 218) {
            name = "motesplatser for unga";
          } else if (entries.getKey() == 22) {
            name = "forskolor";
          } else if (entries.getKey() == 28) {
            name = "gymnasieskolor";
          } else if (entries.getKey() == 12) {
            name = "grundskolor";
          } else if (entries.getKey() == 21) {
            name = "vard- och omsorgsboende";
          } else if (entries.getKey() == 220) {
            name = "seniorboenden";
          } else if (entries.getKey() == 10) {
            name = "bibliotek";
          } else if (entries.getKey() == 23) {
            name = "parker";
          } else if (entries.getKey() == 18) {
            name = "torg";
          } else if (entries.getKey() == 7) {
            name = "idrottsanlaggningar";
          } else if (entries.getKey() == 216) {
            name = "offentliga kommunala toaletter";
          } else if (entries.getKey() == 1) {
            name = "badanlaggningar";
          } else if (entries.getKey() == 14) {
            name = "industriomrade";
          } else if (entries.getKey() == 0) {
            name = "adresser";
          } else {
            // todo search id in source of http://karta2.orebro.se/webb/
            name = "oklassificerat";
          }

          OsmXmlWriter writer = new OsmXmlWriter(new OutputStreamWriter(new FileOutputStream(new File(path, entries.getKey() + "-" + name + ".osm.xml")), "UTF8"));

          for (Item item : entries.getValue()) {


            BooleanQuery duplicateQuery = new BooleanQuery();

            double radiusKilometersDuplicates = 0.5;

            Node node = new Node();
            node.setTags(new LinkedHashMap<String, String>(10));

            node.setLatitude(((Point) item.getGeom()).getLatitude());
            node.setLongitude(((Point) item.getGeom()).getLongitude());


            if (entries.getKey() == 217) {
              // Flexpunkt 200...300?
              node.setTag("name", item.getTitle());
              node.setTag("public_transport", "stop_position");
              node.setTag("bus", "yes");
              node.setTag("operator", "Flextrafiken");


            } else if (entries.getKey() == 219) {
              // bussparkering
              node.setTag("name", item.getTitle());
              node.setTag("amenity", "parking");
              node.setTag("note", "bussparkering"); // todo specialtag?

            } else if (entries.getKey() == 24) {
              // parkering
              node.setTag("name", item.getTitle());
              node.setTag("amenity", "parking");

            } else if (entries.getKey() == 258) {
              // handikapplats
              node.setTag("name", item.getTitle());
              node.setTag("amenity", "parking");
              node.setTag("capacity:disabled", "yes");


            } else if (entries.getKey() == 9) {
              // återvinningscentral
              node.setTag("name", item.getTitle());
              node.setTag("amenity", "recycling");

            } else if (entries.getKey() == 29) {
              // återvinningsstation
              node.setTag("name", item.getTitle());
              node.setTag("amenity", "recycling");

            } else if (entries.getKey() == 17) {
              // återvinning ris och kompost
              node.setTag("name", item.getTitle());
              node.setTag("amenity", "recycling");
              node.setTag("recycling:organic", "yes");
              node.setTag("recycling:christmas_trees", "yes");


            } else if (entries.getKey() == 218) {
              // mötestplats för unga (fritidsgårdar?)
              node.setTag("name", item.getTitle());
              node.setTag("amenity", "community_centre");


            } else if (entries.getKey() == 22) {
              // förskola
              node.setTag("name", item.getTitle());
              node.setTag("amenity", "school");
              node.setTag("isced:level", "1");

            } else if (entries.getKey() == 28) {
              // gymnasieskolor
              node.setTag("name", item.getTitle());
              node.setTag("amenity", "school");
              node.setTag("isced:level", "4");

            } else if (entries.getKey() == 12) {
              // grundskolor
              node.setTag("name", item.getTitle());
              node.setTag("amenity", "school");
              node.setTag("note", "Todo: set isced:level=2 (lågstadie-mellanstadie) or 3 (högstadie)");

            } else if (entries.getKey() == 21) {
              // Vård- och omsorgsboende
              node.setTag("name", item.getTitle());
              node.setTag("amenity", "social_facility");
              node.setTag("social_facility", "assisted_living");

            } else if (entries.getKey() == 220) {
              // Seniorboenden
              node.setTag("name", item.getTitle());
              node.setTag("amenity", "social_facility");
              node.setTag("social_facility", "assisted_living");
              node.setTag("social_facility:for", "senior");

            } else if (entries.getKey() == 10) {
              // bibliotek
              node.setTag("name", item.getTitle());
              node.setTag("amenity", "library");

            } else if (entries.getKey() == 23) {
              // parker
              node.setTag("name", item.getTitle());
              node.setTag("leisure", "park");


            } else if (entries.getKey() == 18) {
              // torg
              node.setTag("name", item.getTitle());
              node.setTag("highway", "pedestrian");
              node.setTag("area", "yes");

            } else if (entries.getKey() == 7) {
              // idrottsanläggningar
              node.setTag("name", item.getTitle());
              node.setTag("note", "Idrottsanläggning");


            } else if (entries.getKey() == 216) {
              // offentliga kommunala toaletter
              node.setTag("name", item.getTitle());
              node.setTag("amenity", "toilets");
              node.setTag("access", "public");
              node.setTag("operator", "Örebro kommun");

            } else if (entries.getKey() == 1) {
              // badanläggningar
              node.setTag("name", item.getTitle());
              node.setTag("amenity", "public bath");

            } else if (entries.getKey() == 14) {
              // Industriområde
              node.setTag("name", item.getTitle());
              node.setTag("landuse", "industrial");

              duplicateQuery.add(index.getQueryFactories().containsTagKeyAndValueQueryFactory()
                  .setKey("name")
                  .setValue(item.getTitle())
                  .build(), BooleanClause.Occur.MUST);


            } else if (entries.getKey() == 0) {

              // gator, husnummer, rondeller? broar?

              if ("StreetAddress".equals(item.getType())) {

                if ("Landsbygd".equals(item.getTown())) {

                  Matcher matcher = streetAddressPattern.matcher(item.getTitle());
                  if (!matcher.matches()) {
                    node.setTag("name", item.getTitle());
                    node.setTag("place", "hamlet");

                    radiusKilometersDuplicates = 10;

                  } else {
                    node.setTag("addr:place", matcher.group(1));
                    node.setTag("addr:housenumber", matcher.group(2));

                    duplicateQuery.add(index.getQueryFactories().containsTagKeyAndValueQueryFactory()
                        .setKey("addr:place")
                        .setValue(node.getTag("addr:place"))
                        .build(), BooleanClause.Occur.MUST);

                    duplicateQuery.add(index.getQueryFactories().containsTagKeyAndValueQueryFactory()
                        .setKey("addr:housenumber")
                        .setValue(node.getTag("addr:housenumber"))
                        .build(), BooleanClause.Occur.MUST);

                    radiusKilometersDuplicates = 5;

                  }

                } else {

                  Matcher matcher = streetAddressPattern.matcher(item.getTitle());
                  if (matcher.matches()) {

                    node.setTag("addr:street", matcher.group(1));
                    node.setTag("addr:housenumber", matcher.group(2));

                    duplicateQuery.add(index.getQueryFactories().containsTagKeyAndValueQueryFactory()
                        .setKey("addr:street")
                        .setValue(node.getTag("addr:street"))
                        .build(), BooleanClause.Occur.MUST);

                    duplicateQuery.add(index.getQueryFactories().containsTagKeyAndValueQueryFactory()
                        .setKey("addr:housenumber")
                        .setValue(node.getTag("addr:housenumber"))
                        .build(), BooleanClause.Occur.MUST);


                  } else {

                    node.setTag("higway", "road");
                    node.setTag("name", item.getTitle());

                    radiusKilometersDuplicates = 2;


                  }

                }


              } else {
                // RealEstate
                // unsupported type todo log
                continue;
              }

            } else {
              node.setTag("name", item.getTitle());
              node.setTag("note", "Todo: oklassficierad");

            }


            if (item.getCity() != null) {
              node.setTag("addr:city", item.getCity());

              if (node.getTag("addr:place") == null
                  && !item.getCity().equals(item.getTown())
                  && !"Landsbygd".equals(item.getTown())) {

                node.setTag("addr:place", item.getTown());
              }
            }


            if (duplicateQuery.getClauses().length == 0) {
              duplicateQuery.add(index.getQueryFactories().containsTagKeyAndValueQueryFactory()
                  .setKey("name")
                  .setValue(item.getTitle())
                  .build(), BooleanClause.Occur.MUST);
            }

            BooleanQuery classQuery = new BooleanQuery();

            classQuery.add(index.getQueryFactories().nodeRadialEnvelopeQueryFactory()
                .setKilometerRadius(radiusKilometersDuplicates)
                .setLongitude(((Point) item.getGeom()).getLongitude())
                .setLatitude(((Point) item.getGeom()).getLatitude())
                .build()
                , BooleanClause.Occur.SHOULD);

            classQuery.add(index.getQueryFactories().wayRadialEnvelopeQueryFactory()
                .setKilometerRadius(radiusKilometersDuplicates)
                .setLongitude(((Point) item.getGeom()).getLongitude())
                .setLatitude(((Point) item.getGeom()).getLatitude())
                .build()
                , BooleanClause.Occur.SHOULD);

            duplicateQuery.add(classQuery
                , BooleanClause.Occur.MUST);


            Set<OsmObject> hits = index.search(duplicateQuery).keySet();

            if (!hits.isEmpty()) {
              for (OsmObject hit : hits) {
                hit.accept(writeToOsmDuplicates);
              }
//              node.setTag("ref:se:orebro:layerId", String.valueOf(item.getLayerId()));
//              node.setTag("ref:se:orebro:city", String.valueOf(item.getCity()));
//              node.setTag("ref:se:orebro:town", String.valueOf(item.getTown()));
              orebroDuplicatesWriter.write(node);

            } else {

              node.setId(id--);
              writer.write(node);

            }


          }

          writer.close();
        }


      }


      osmDuplicatesWriter.close();
      orebroDuplicatesWriter.close();

    } finally {
      overpass.close();
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
