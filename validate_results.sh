#! /bin/bash
BASENAME='Australian_Government_Information_Security_Manual'
echo $BASENAME
VERSION=$BASENAME'_JAN20'
echo $VERSION

CATALOG=$VERSION'_catalog.xml'
echo "Validating catalog:" $CATALOG
~/OSCAL/src/utils/util/oscal-content-validator.py --oscal-schema ~/OSCAL/xml/schema/oscal_catalog_schema.xsd --oscal-file $CATALOG

for i in 'TS' 'S' 'P' 'O' 'Essential8'
do
  PROFILE=$VERSION'_'$i'_profile.xml'
  echo "Validating profile:" $PROFILE
  ~/OSCAL/src/utils/util/oscal-content-validator.py --oscal-schema ~/OSCAL/xml/schema/oscal_profile_schema.xsd --oscal-file $PROFILE
done

for COMPONENT in components/organisation/cybersecurityroles.xml \
   components/systemx/cybersecurityroles.xml
do
    echo "Validating component:" $COMPONENT
    ~/OSCAL/src/utils/util/oscal-content-validator.py --oscal-schema ~/OSCAL/xml/schema/oscal_component_schema.xsd --oscal-file $COMPONENT
done