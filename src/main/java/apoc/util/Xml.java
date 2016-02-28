package apoc.util;

import apoc.util.result.MapResult;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class Xml {

    public static final XMLInputFactory FACTORY = XMLInputFactory.newFactory();

    @Context public GraphDatabaseService db;

    @Procedure
    public Stream<MapResult> loadXml(@Name("url") String url) {
        try {
            URLConnection urlConnection = new URL(url).openConnection();
            XMLStreamReader reader = FACTORY.createXMLStreamReader(urlConnection.getInputStream());
            if (reader.nextTag()==XMLStreamConstants.START_ELEMENT) {
                return Stream.of(new MapResult(handleElement(reader)));
            }
            throw new RuntimeException("Can't read url " + url + " as XML");
        } catch (IOException | XMLStreamException e) {
            throw new RuntimeException("Can't read url " + url + " as XML", e);
        }
    }

    private Map<String, Object> handleElement(XMLStreamReader reader) throws XMLStreamException {
        LinkedHashMap<String, Object> row = null;
        String element = null;
        if (reader.isStartElement()) {
            int attributes = reader.getAttributeCount();
            row = new LinkedHashMap<>(attributes + 3);
            element = reader.getLocalName();
            row.put("_type", element);
            for (int a = 0; a < attributes; a++) {
                row.put(reader.getAttributeLocalName(a), reader.getAttributeValue(a));
            }
            next(reader);
            if (reader.hasText()) {
                row.put("_text",reader.getText().trim());
                next(reader);
            }
            if (reader.isStartElement()) {
                List<Map<String, Object>> children = new ArrayList<>(100);
                do {
                    Map<String, Object> child = handleElement(reader);
                    if (child != null && !child.isEmpty()) {
                        children.add(child);
                    }
                } while (next(reader) == XMLStreamConstants.START_ELEMENT);
                if (!children.isEmpty()) row.put("_children", children);
            }
            if (reader.isEndElement() || reader.getEventType() == XMLStreamConstants.END_DOCUMENT) {
                return row;
            }
        }
        throw new IllegalStateException("Incorrect end-element state "+reader.getEventType()+" after "+element);
    }

    private int next(XMLStreamReader reader) throws XMLStreamException {
        reader.next();
        while (reader.isWhiteSpace()) reader.next();
        return reader.getEventType();
    }
}
