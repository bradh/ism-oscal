package net.frogmouth.rnd.xml2oscal;

import gov.nist.secauto.metaschema.binding.BindingContext;
import gov.nist.secauto.metaschema.binding.io.BindingException;
import gov.nist.secauto.metaschema.binding.io.Feature;
import gov.nist.secauto.metaschema.binding.io.Format;
import gov.nist.secauto.metaschema.binding.io.MutableConfiguration;
import gov.nist.secauto.metaschema.binding.io.Serializer;
import gov.nist.secauto.metaschema.datatypes.markup.MarkupLine;
import gov.nist.secauto.metaschema.datatypes.markup.MarkupMultiline;
import gov.nist.secauto.oscal.lib.model.BackMatter;
import gov.nist.secauto.oscal.lib.model.BackMatter.Resource;
import gov.nist.secauto.oscal.lib.model.BackMatter.Resource.Rlink;
import gov.nist.secauto.oscal.lib.model.Catalog;
import gov.nist.secauto.oscal.lib.model.Control;
import gov.nist.secauto.oscal.lib.model.GrouposcalCatalog;
import gov.nist.secauto.oscal.lib.model.Import;
import gov.nist.secauto.oscal.lib.model.Merge;
import gov.nist.secauto.oscal.lib.model.Metadata;
import gov.nist.secauto.oscal.lib.model.Part;
import gov.nist.secauto.oscal.lib.model.Profile;
import gov.nist.secauto.oscal.lib.model.Property;
import gov.nist.secauto.oscal.lib.model.SelectControlByIdoscalProfile;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import net.frogmouth.rnd.xml2oscal.generated.ControlType;
import net.frogmouth.rnd.xml2oscal.generated.ISMType;
import org.apache.commons.codec.EncoderException;
import org.apache.commons.codec.net.URLCodec;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

class Xml2Oscal {

    private static final String OSCAL_XML_MEDIA_TYPE = "application/oscal.catalog+xml";
    private final URI OUR_NS;
    private ISMType ism;
    Catalog catalog;
    private Map<String, List<String>> essential8mapping = new HashMap<>();
    private String e8MappingTitle = "Essential Eight to ISM Mapping";
    private String e8MappingVersion = "TBD";

    Xml2Oscal() throws URISyntaxException {
        OUR_NS = new URI("urn:uuid:5dab2ee4-11be-11ea-865a-672481b505d3");
    }

    void readFrom(String ismFileName) {
        try {
            JAXBContext jaxbContext =
                    JAXBContext.newInstance(
                            net.frogmouth.rnd.xml2oscal.generated.ObjectFactory.class);
            Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
            JAXBElement element =
                    (JAXBElement) jaxbUnmarshaller.unmarshal(new FileReader(ismFileName));
            ism = (ISMType) element.getValue();
        } catch (JAXBException ex) {
            ex.printStackTrace();
        } catch (FileNotFoundException ex) {
            ex.printStackTrace();
        }
    }

    void writeTo(String oscalFileNameBase, String version) {
        String catalogName = writeCatalog(oscalFileNameBase, version);
        writeProfiles(oscalFileNameBase, version, catalogName);
    }

    private String writeCatalog(String oscalFileNameBase, String version) {
        String catalogName = oscalFileNameBase + " - Catalog.xml";
        catalog = new Catalog();
        catalog.setUuid(UUID.randomUUID());
        catalog.setMetadata(buildCatalogMetadata(version));
        catalog.setGroups(getGroups());
        serialiseCatalog(catalog, catalogName);
        return catalogName;
    }

    private Metadata buildCatalogMetadata(String version) {
        Metadata metadata = new Metadata();
        metadata.setTitle(getCatalogTitle(version));
        metadata.setRemarks(
                MarkupMultiline.fromMarkdown("Converted from ACSC ISM release " + version));
        metadata.setVersion(version);
        metadata.setPublished(ZonedDateTime.now(ZoneOffset.UTC));
        metadata.setLastModified(ZonedDateTime.now(ZoneOffset.UTC));
        metadata.setOscalVersion("1.0.0");
        return metadata;
    }

    private static MarkupLine getCatalogTitle(String version) {
        return MarkupLine.fromMarkdown(
                "Australian Government Information Security Manual "
                        + version
                        + " - OSCAL Catalog");
    }

    private LinkedList<GrouposcalCatalog> getGroups() {
        LinkedList<GrouposcalCatalog> groups = new LinkedList<>();
        List<String> guidelines = new ArrayList<>();
        for (ControlType control : ism.getControl()) {
            if (!guidelines.contains(control.getGuideline())) {
                guidelines.add(control.getGuideline());
            }
        }
        for (String guidelineName : guidelines) {
            GrouposcalCatalog group = new GrouposcalCatalog();
            group.setTitle(MarkupLine.fromMarkdown(guidelineName));
            group.setGroups(getSubgroupsFromSections(guidelineName));
            groups.add(group);
        }
        return groups;
    }

    private LinkedList<GrouposcalCatalog> getSubgroupsFromSections(String guidelineName) {
        LinkedList<GrouposcalCatalog> groups = new LinkedList<>();
        List<String> sections = new ArrayList<>();
        for (ControlType control : ism.getControl()) {
            if (!control.getGuideline().equals(guidelineName)) {
                continue;
            }
            if (!sections.contains(control.getSection())) {
                sections.add(control.getSection());
            }
        }
        for (String sectionName : sections) {
            GrouposcalCatalog group = new GrouposcalCatalog();
            group.setTitle(MarkupLine.fromMarkdown(sectionName));
            group.setControls(getControlsForSections(guidelineName, sectionName));
            groups.add(group);
        }
        return groups;
    }

    private LinkedList<Control> getControlsForSections(String guidelineName, String sectionName) {
        LinkedList<Control> controls = new LinkedList<>();
        for (ControlType controlType : ism.getControl()) {
            if (controlType.getGuideline().equals(guidelineName)
                    && controlType.getSection().equals(sectionName)) {
                Control control = new Control();
                control.setTitle(MarkupLine.fromMarkdown(controlType.getTopic()));
                control.setId(getControlIdentifier(controlType));
                Part statementPart = new Part();
                statementPart.setName("SecurityControl" + controlType.getIdentifier());
                statementPart.setClazz("statement");
                statementPart.setProse(
                        MarkupMultiline.fromHtml("<p>" + controlType.getDescription() + "</p>"));
                LinkedList<Part> parts = new LinkedList<>();
                parts.add(statementPart);
                control.setParts(parts);
                LinkedList<Property> properties = new LinkedList<>();
                Property labelProperty = new Property();
                labelProperty.setName("label");
                labelProperty.setValue("Security Control " + controlType.getIdentifier());
                properties.add(labelProperty);
                Property revisionProperty = new Property();
                revisionProperty.setNs(OUR_NS);
                revisionProperty.setName("Revision");
                revisionProperty.setValue(controlType.getRevision().toString());
                properties.add(revisionProperty);
                Property updatedProperty = new Property();
                updatedProperty.setNs(OUR_NS);
                updatedProperty.setName("Updated");
                updatedProperty.setValue(controlType.getUpdated());
                properties.add(updatedProperty);
                control.setProps(properties);
                controls.add(control);
            }
        }
        return controls;
    }

    private static String getControlIdentifier(ControlType controlType) {
        return getControlIdentifier(controlType.getIdentifier());
    }

    private static String getControlIdentifier(String identifier) {
        return "controlid-" + identifier.trim();
    }

    private void serialiseCatalog(Catalog catalog, String fileName) {
        try {
            BindingContext context = BindingContext.newInstance();
            MutableConfiguration config =
                    new MutableConfiguration().enableFeature(Feature.SERIALIZE_ROOT);
            File out = new File(fileName);
            Serializer<Catalog> serializer =
                    context.newSerializer(Format.XML, Catalog.class, config);
            serializer.serialize(catalog, out);
        } catch (BindingException | FileNotFoundException ex) {
            Logger.getLogger(Xml2Oscal.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void writeProfiles(String oscalFileNameBase, String version, String catalogName) {
        writeOfficialProfile(oscalFileNameBase, version, catalogName);
        writeProtectedProfile(oscalFileNameBase, version, catalogName);
        writeSecretProfile(oscalFileNameBase, version, catalogName);
        writeTopSecretProfile(oscalFileNameBase, version, catalogName);
        writeEssentialEightProfiles(oscalFileNameBase, version, catalogName);
    }

    private void writeOfficialProfile(
            String oscalFileNameBase, String version, String catalogName) {
        try {
            String profileName = oscalFileNameBase + " - O Profile.xml";
            Profile profile = new Profile();
            String remarks = "Converted from ACSC ISM release " + version;
            profile.setMetadata(buildProfileMetadata("OFFICIAL", version, remarks));
            LinkedList<String> controlIds = new LinkedList<>();
            for (ControlType controlType : ism.getControl()) {
                if (controlType.getOFFICIAL().toLowerCase().equals("yes")) {
                    controlIds.add(getControlIdentifier(controlType));
                }
            }
            buildProfile(profile, controlIds, catalogName, version);
            serialiseProfile(profile, profileName);
        } catch (URISyntaxException | EncoderException ex) {
            Logger.getLogger(Xml2Oscal.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void writeProtectedProfile(
            String oscalFileNameBase, String version, String catalogName) {
        try {
            String profileName = oscalFileNameBase + " - P Profile.xml";
            Profile profile = new Profile();
            String remarks = "Converted from ACSC ISM release " + version;
            profile.setMetadata(buildProfileMetadata("PROTECTED", version, remarks));
            LinkedList<String> controlIds = new LinkedList<>();
            for (ControlType controlType : ism.getControl()) {
                if (controlType.getPROTECTED().toLowerCase().equals("yes")) {
                    controlIds.add(getControlIdentifier(controlType));
                }
            }
            buildProfile(profile, controlIds, catalogName, version);
            serialiseProfile(profile, profileName);
        } catch (URISyntaxException | EncoderException ex) {
            Logger.getLogger(Xml2Oscal.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void writeSecretProfile(String oscalFileNameBase, String version, String catalogName) {
        try {
            String profileName = oscalFileNameBase + " - S Profile.xml";
            Profile profile = new Profile();
            String remarks = "Converted from ACSC ISM release " + version;
            profile.setMetadata(buildProfileMetadata("SECRET", version, remarks));
            LinkedList<String> controlIds = new LinkedList<>();
            for (ControlType controlType : ism.getControl()) {
                if (controlType.getSECRET().toLowerCase().equals("yes")) {
                    controlIds.add(getControlIdentifier(controlType));
                }
            }
            buildProfile(profile, controlIds, catalogName, version);
            serialiseProfile(profile, profileName);
        } catch (URISyntaxException | EncoderException ex) {
            Logger.getLogger(Xml2Oscal.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void writeTopSecretProfile(
            String oscalFileNameBase, String version, String catalogName) {
        try {
            String profileName = oscalFileNameBase + " - TS Profile.xml";
            Profile profile = new Profile();
            String remarks = "Converted from ACSC ISM release " + version;
            profile.setMetadata(buildProfileMetadata("TOP SECRET", version, remarks));
            LinkedList<String> controlIds = new LinkedList<>();
            for (ControlType controlType : ism.getControl()) {
                if (controlType.getTOPSECRET().toLowerCase().equals("yes")) {
                    controlIds.add(getControlIdentifier(controlType));
                }
            }
            buildProfile(profile, controlIds, catalogName, version);
            serialiseProfile(profile, profileName);
        } catch (URISyntaxException | EncoderException ex) {
            Logger.getLogger(Xml2Oscal.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void writeEssentialEightProfiles(
            String oscalFileNameBase, String version, String catalogName) {
        for (String level : this.essential8mapping.keySet()) {
            try {
                String profileName = oscalFileNameBase + " - E8 " + level + " Profile.xml";
                Profile profile = new Profile();
                String remarks =
                        "Converted from ACSC ISM release "
                                + version
                                + " and the ACSC "
                                + e8MappingTitle
                                + " dated "
                                + e8MappingVersion;
                profile.setMetadata(buildProfileMetadata("Essential 8 " + level, version, remarks));
                LinkedList<String> controlIds = new LinkedList<>();
                List<String> controlsForLevel = this.essential8mapping.get(level);
                for (String controlId : controlsForLevel) {
                    controlIds.add(getControlIdentifier(controlId));
                }
                buildProfile(profile, controlIds, catalogName, version);
                serialiseProfile(profile, profileName);
            } catch (URISyntaxException | EncoderException ex) {
                Logger.getLogger(Xml2Oscal.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    private void buildProfile(
            Profile profile, LinkedList<String> controlIds, String catalogName, String version)
            throws URISyntaxException, EncoderException {
        profile.setUuid(UUID.randomUUID());
        SelectControlByIdoscalProfile select = new SelectControlByIdoscalProfile();
        select.setWithIds(controlIds);
        LinkedList<Import> imports = new LinkedList<>();
        Import imprt = new Import();
        LinkedList<SelectControlByIdoscalProfile> includes = new LinkedList<>();
        imprt.setHref(getCatalogURI(catalogName));
        includes.add(select);
        imprt.setIncludeControls(includes);
        imports.add(imprt);
        profile.setImports(imports);
        Merge merge = new Merge();
        merge.setAsIs(Boolean.TRUE);
        profile.setMerge(merge);
        BackMatter backMatter = new BackMatter();
        LinkedList<Resource> resources = new LinkedList<>();
        Resource catalogResource = new Resource();
        Rlink catalogRLinkXML = new Rlink();
        catalogRLinkXML.setHref(getCatalogURI(catalogName));
        catalogRLinkXML.setMediaType(OSCAL_XML_MEDIA_TYPE);
        LinkedList<Rlink> catalogRLinks = new LinkedList<>();
        catalogRLinks.add(catalogRLinkXML);
        catalogResource.setRlinks(catalogRLinks);
        catalogResource.setDescription(
                MarkupMultiline.fromMarkdown(getCatalogTitle(version).toMarkdown()));
        catalogResource.setUuid(catalog.getUuid());
        resources.add(catalogResource);
        backMatter.setResources(resources);
        profile.setBackMatter(backMatter);
    }

    private static URI getCatalogURI(String catalogName)
            throws URISyntaxException, EncoderException {
        URLCodec codec = new URLCodec();
        return URI.create(
                codec.encode(
                        "https://raw.githubusercontent.com/bradh/ism-oscal/main/" + catalogName));
    }

    private Metadata buildProfileMetadata(String level, String version, String remarks) {
        Metadata metadata = new Metadata();
        metadata.setTitle(
                MarkupLine.fromMarkdown(
                        "Australian Government Information Security Manual - "
                                + level
                                + " Profile"));
        metadata.setRemarks(MarkupMultiline.fromMarkdown(remarks));
        metadata.setVersion(version);
        metadata.setPublished(ZonedDateTime.now(ZoneOffset.UTC));
        metadata.setLastModified(ZonedDateTime.now(ZoneOffset.UTC));
        metadata.setOscalVersion("1.0.0");
        return metadata;
    }

    private void serialiseProfile(Profile profile, String profileName) {
        try {
            BindingContext context = BindingContext.newInstance();
            MutableConfiguration config =
                    new MutableConfiguration().enableFeature(Feature.SERIALIZE_ROOT);
            File out = new File(profileName);
            Serializer<Profile> serializer =
                    context.newSerializer(Format.XML, Profile.class, config);
            serializer.serialize(profile, out);
        } catch (BindingException | FileNotFoundException ex) {
            Logger.getLogger(Xml2Oscal.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    void extractE8Mappings(String html) throws IOException {
        File input = new File(html);
        Document doc = Jsoup.parse(input, "UTF-8", "");
        Elements h1 = doc.select("H1");
        this.e8MappingTitle = h1.text();
        Elements lastUpdatedField = doc.select(".views-field-field-date-user-updated");
        Elements lastUpdatedValue = lastUpdatedField.select(".field-content");
        this.e8MappingVersion = lastUpdatedValue.text();
        Elements tables = doc.select("table");
        if (tables.size() != 1) {
            throw new IOException("Did not find a single table in the Essential 8 mapping");
        }
        Element table = tables.get(0);
        Elements header = table.select("thead > tr > th > p");
        if (header.size() != 3) {
            throw new IOException(
                    "Did not find expected number of column headers (3), but got :"
                            + header.size());
        }
        if (!header.get(0).ownText().equals("Mitigation Strategy")) {
            throw new IOException(
                    "Did not find expected column 0 header, but got: " + header.get(0).ownText());
        }
        for (int c = 1; c < header.size(); c++) {
            String e8Level = header.get(c).ownText();
            this.essential8mapping.put(e8Level, new ArrayList<>());
        }
        Elements rows = table.select("tbody > tr");
        for (int r = 0; r < rows.size(); r++) {
            Element row = rows.get(r);
            Elements cellsInThisRow = row.select("td > p");
            if (cellsInThisRow.size() != header.size()) {
                throw new IOException(
                        "Row / header mismatch at row:"
                                + r
                                + ", "
                                + cellsInThisRow.size()
                                + " vs"
                                + header.size());
            }
            for (int c = 1; c < cellsInThisRow.size(); c++) {
                Element cell = cellsInThisRow.get(c);
                String cellText = cell.text();
                String[] controlIds = cellText.split(",");
                String e8Level = header.get(c).ownText();
                for (String controlId : controlIds) {
                    this.essential8mapping.get(e8Level).add(controlId);
                }
            }
        }
        // System.out.println(this.essential8mapping.entrySet());
    }
}
