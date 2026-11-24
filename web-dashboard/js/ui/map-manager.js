// map-manager.js - MapLibre GL JS + OpenStreetMap Interactive Map Engine

const HomeMonitorMapConfig = {
    tileUrl: "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank">OpenStreetMap</a> contributors',
    defaultCenter: [-122.4194, 37.7749], // [lng, lat]
    defaultZoom: 14
};

class HomeMonitorMapManager {
    constructor(containerId) {
        this.containerId = containerId;
        this.map = null;
        this.marker = null;
        this.initialized = false;
    }

    init() {
        if (this.initialized || typeof maplibregl === "undefined") return;
        const container = document.getElementById(this.containerId);
        if (!container) return;

        try {
            this.map = new maplibregl.Map({
                container: this.containerId,
                style: {
                    version: 8,
                    sources: {
                        "osm-tiles": {
                            type: "raster",
                            tiles: [HomeMonitorMapConfig.tileUrl],
                            tileSize: 256,
                            attribution: HomeMonitorMapConfig.attribution
                        }
                    },
                    layers: [{
                        id: "osm-tiles-layer",
                        type: "raster",
                        source: "osm-tiles",
                        minzoom: 0,
                        maxzoom: 19
                    }]
                },
                center: HomeMonitorMapConfig.defaultCenter,
                zoom: HomeMonitorMapConfig.defaultZoom
            });

            this.map.addControl(new maplibregl.NavigationControl());
            this.initialized = true;
            console.log(`[MapManager] Initialized MapLibre OSM map in container '#${this.containerId}'`);
        } catch (e) {
            console.error(`[MapManager] Failed to initialize map in '#${this.containerId}':`, e);
        }
    }

    updateLocation(lat, lng, accuracy = 5.0, label = "Target Device") {
        if (!this.initialized) this.init();
        if (!this.map) return;

        const coords = [lng, lat];
        this.map.flyTo({ center: coords, zoom: 15 });

        if (!this.marker) {
            const el = document.createElement('div');
            el.className = 'homemonitor-map-pin';
            el.innerHTML = '<i class="fa-solid fa-location-dot text-danger fs-2"></i>';
            this.marker = new maplibregl.Marker({ element: el })
                .setLngLat(coords)
                .setPopup(new maplibregl.Popup({ offset: 25 }).setText(`${label} (Lat: ${lat.toFixed(4)}, Lng: ${lng.toFixed(4)})`))
                .addTo(this.map);
        } else {
            this.marker.setLngLat(coords);
        }
    }
}

// Global Singleton Instance
const activeGpsMapManager = new HomeMonitorMapManager("gpsMapContainer");
