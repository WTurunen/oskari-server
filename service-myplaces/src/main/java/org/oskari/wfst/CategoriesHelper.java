package org.oskari.wfst;

import java.io.IOException;
import java.io.OutputStream;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import fi.nls.oskari.domain.map.MyPlaceCategory;

public class CategoriesHelper extends WFSTHelper {

    private static final String TYPENAME_CATEGORIES = "feature:categories";

    public static void insertCategories(OutputStream out, MyPlaceCategory[] categories)
            throws XMLStreamException, IOException {
        XMLStreamWriter xsw = XOF.createXMLStreamWriter(out);
        writeStartTransaction(xsw);
        xsw.writeNamespace(PREFIX_OSKARI, OSKARI);
        for (MyPlaceCategory category : categories) {
            insertCategory(xsw, category);
        }
        xsw.writeEndElement();
        xsw.writeEndDocument();
        xsw.close();
    }

    public static void updateCategories(OutputStream out, MyPlaceCategory[] categories)
            throws XMLStreamException, IOException {
        XMLStreamWriter xsw = XOF.createXMLStreamWriter(out);
        writeStartTransaction(xsw);
        xsw.writeNamespace(PREFIX_OSKARI, OSKARI);
        for (MyPlaceCategory category : categories) {
            updateCategory(xsw, category);
        }
        xsw.writeEndElement();
        xsw.writeEndDocument();
        xsw.close();
    }

    public static void deleteCategories(OutputStream out, long[] categoryIds)
            throws XMLStreamException {
        XMLStreamWriter xsw = XOF.createXMLStreamWriter(out);
        writeStartTransaction(xsw);
        xsw.writeNamespace(PREFIX_OSKARI, OSKARI);
        for (long categoryId : categoryIds) {
            deleteCategory(xsw, categoryId);
        }
        xsw.writeEndElement();
        xsw.writeEndDocument();
        xsw.close();
    }

    private static void insertCategory(XMLStreamWriter xsw, MyPlaceCategory category)
            throws XMLStreamException {
        xsw.writeStartElement(WFS, "Insert");
        xsw.writeAttribute("typeName", TYPENAME_CATEGORIES);
        xsw.writeStartElement(OSKARI, "categories");

        writeTextElement(xsw, OSKARI, "default", Boolean.toString(category.isDefault()));
        writeTextElement(xsw, OSKARI, "category_name", category.getCategory_name());
        writeTextElement(xsw, OSKARI, "stroke_width", category.getStroke_width());
        writeTextElement(xsw, OSKARI, "stroke_color", category.getStroke_color());
        writeTextElement(xsw, OSKARI, "fill_color", category.getFill_color());
        writeTextElement(xsw, OSKARI, "uuid", category.getUuid());
        writeTextElement(xsw, OSKARI, "dot_color", category.getDot_color());
        writeTextElement(xsw, OSKARI, "dot_size", category.getDot_size());
        writeTextElement(xsw, OSKARI, "border_width", category.getBorder_width());
        writeTextElement(xsw, OSKARI, "border_color", category.getBorder_color());
        writeTextElement(xsw, OSKARI, "publisher_name", category.getPublisher_name());
        writeTextElement(xsw, OSKARI, "dot_shape", category.getDot_shape());
        writeTextElement(xsw, OSKARI, "stroke_linejoin", category.getStroke_linejoin());
        writeTextElement(xsw, OSKARI, "fill_pattern", category.getFill_pattern());
        writeTextElement(xsw, OSKARI, "stroke_linecap", category.getCategory_name());
        writeTextElement(xsw, OSKARI, "stroke_dasharray", category.getCategory_name());
        writeTextElement(xsw, OSKARI, "border_linejoin", category.getCategory_name());
        writeTextElement(xsw, OSKARI, "border_dasharray", category.getCategory_name());

        xsw.writeEndElement(); // Close <feature:my_places>
        xsw.writeEndElement(); // Close <wfs:Insert>
    }

    private static void updateCategory(XMLStreamWriter xsw, MyPlaceCategory category)
            throws XMLStreamException {
        xsw.writeStartElement(WFS, "Update");
        xsw.writeAttribute("typeName", TYPENAME_CATEGORIES);

        writeProperty(xsw, "default", Boolean.toString(category.isDefault()));
        writeProperty(xsw, "category_name", category.getCategory_name());
        writeProperty(xsw, "stroke_width", category.getStroke_width());
        writeProperty(xsw, "stroke_color", category.getStroke_color());
        writeProperty(xsw, "fill_color", category.getFill_color());
        writeProperty(xsw, "uuid", category.getUuid());
        writeProperty(xsw, "dot_color", category.getDot_color());
        writeProperty(xsw, "dot_size", category.getDot_size());
        writeProperty(xsw, "border_width", category.getBorder_width());
        writeProperty(xsw, "border_color", category.getBorder_color());
        writeProperty(xsw, "publisher_name", category.getPublisher_name());
        writeProperty(xsw, "dot_shape", category.getDot_shape());
        writeProperty(xsw, "stroke_linejoin", category.getStroke_linejoin());
        writeProperty(xsw, "fill_pattern", category.getFill_pattern());
        writeProperty(xsw, "stroke_linecap", category.getCategory_name());
        writeProperty(xsw, "stroke_dasharray", category.getCategory_name());
        writeProperty(xsw, "border_linejoin", category.getCategory_name());
        writeProperty(xsw, "border_dasharray", category.getCategory_name());

        writeFeatureIdFilter(xsw, prefixId(category.getId()));

        xsw.writeEndElement(); // close <wfs:Update>
    }

    private static void deleteCategory(XMLStreamWriter xsw, long id)
            throws XMLStreamException {
        xsw.writeStartElement(WFS, "Delete");
        xsw.writeAttribute("typeName", TYPENAME_CATEGORIES);
        writeFeatureIdFilter(xsw, prefixId(id));
        xsw.writeEndElement();
    }

    private static String prefixId(long id) {
        return "categories." + id;
    }

}
