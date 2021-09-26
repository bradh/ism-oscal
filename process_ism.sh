SOURCE=https://www.cyber.gov.au/sites/default/files/
MONTH_PART=$(date +%Y-%m)
FILE_DATE_PART=$(date +%B\ %Y)
ZIP_FILE_NAME="ISM ${FILE_DATE_PART} (${FILE_DATE_PART}).zip"
DOWNLOADPATH=$SOURCE$MONTH_PART/$ZIP_FILE_NAME

E8_MAPPING="essential-eight-ism-mapping"
E8_MAPPING_DOWNLOADPATH=https://www.cyber.gov.au/acsc/view-all-content/publications/$E8_MAPPING

if [ ! -f "$ZIP_FILE_NAME" ]; then
    wget "$DOWNLOADPATH"
fi

if [ ! -f "$E8_MAPPING" ]; then
    wget "$E8_MAPPING_DOWNLOADPATH"
fi

WORK_DIR=$(mktemp -d -t ism-oscal-XXXXXXXXXXXXXXX)
unzip -q "$ZIP_FILE_NAME" -d $WORK_DIR

java -jar ozcal/xml2oscal/target/xml2oscal-1.0-SNAPSHOT-jar-with-dependencies.jar \
    "$WORK_DIR/ISM - List of Security Controls ($FILE_DATE_PART).xml" \
    "Australian Government Information Security Manual ($FILE_DATE_PART)" \
    $MONTH_PART \
    "$E8_MAPPING"

rm -rf $WORK_DIR
