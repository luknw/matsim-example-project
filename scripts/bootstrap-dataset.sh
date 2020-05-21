#!/usr/bin/env bash

set -ex

cd scenarios/krakow

# download dataset
if [[ ! -e malopolskie-latest.osm.pbf ]]; then
    wget http://download.geofabrik.de/europe/poland/malopolskie-latest.osm.pbf
fi

# process latitude/longitude rectangle containing Krakow
function osmosis_extract_krakow() {
    osmosis \
        --read-pbf file="malopolskie-latest.osm.pbf" \
        --bounding-box left=19.6854 right=20.3240 top=50.1668 bottom=49.9265 completeWays=yes \
        "$@"
}

# extract roads
osmosis_extract_krakow --used-node --write-xml file="krakow_roads.osm"

# extract traffic signals
osmosis_extract_krakow \
    --node-key-value keyValueList="highway.traffic_signals" \
    --write-xml file="krakow_traffic_signals.osm"
