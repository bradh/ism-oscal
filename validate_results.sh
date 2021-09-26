#! /bin/bash
PATH_TO_OSCAL=~/devsecops/OSCAL
BASENAME='Australian Government Information Security Manual'
FILE_DATE_PART=$(date +%B\ %Y)
VERSION=$BASENAME' ('$FILE_DATE_PART')'
echo $VERSION

CATALOG=$VERSION' - Catalog.xml'
echo "Validating catalog:" $CATALOG
$PATH_TO_OSCAL/src/utils/util/oscal-content-validator.py --oscal-schema $PATH_TO_OSCAL/xml/schema/oscal_catalog_schema.xsd --oscal-file "$CATALOG"

for i in 'TS' 'S' 'P' 'O' 'E8 Maturity Level Two' 'E8 Maturity Level Three'
do
  PROFILE=$VERSION' - '$i' Profile.xml'
  echo "Validating profile:" $PROFILE
  $PATH_TO_OSCAL/src/utils/util/oscal-content-validator.py --oscal-schema $PATH_TO_OSCAL/xml/schema/oscal_profile_schema.xsd --oscal-file "$PROFILE"
done

#for COMPONENT in components/organisation/cybersecurityroles.xml \
#   components/systemx/cybersecurityroles.xml
#do
#    echo "Validating component:" $COMPONENT
#    $PATH_TO_OSCAL/src/utils/util/oscal-content-validator.py --oscal-schema $PATH_TO_OSCAL/xml/schema/oscal_component_schema.xsd --oscal-file $COMPONENT
#done