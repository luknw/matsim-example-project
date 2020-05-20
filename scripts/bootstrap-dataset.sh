#!/usr/bin/env bash

set -ex

cd scenarios/krakow

if [[ ! -e malopolskie-latest.osm.pbf ]]; then
    wget http://download.geofabrik.de/europe/poland/malopolskie-latest.osm.pbf
fi

osmosis \
    --read-pbf file="malopolskie-latest.osm.pbf" \
    --bounding-box left=19.6854 right=20.3240 top=50.1668 bottom=49.9265 completeWays=true \
    --used-node \
    --write-xml file="krakow_roads.osm"
