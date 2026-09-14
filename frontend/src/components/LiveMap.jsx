import { MapContainer, TileLayer, Marker, Popup, Polyline, useMap } from 'react-leaflet';
import 'leaflet/dist/leaflet.css';
import L from 'leaflet';
import { useEffect, useRef } from 'react';

const motoIcon = L.divIcon({
  className: 'courier-moto-marker',
  html: `
    <div class="moto-badge" title="Kurye">
      <svg viewBox="0 0 64 64" width="31" height="31" aria-hidden="true">
        <circle cx="32" cy="32" r="29" fill="#ffffff" stroke="#2563eb" stroke-width="3"/>
        <g fill="none" stroke="#1e3a5f" stroke-width="2.7" stroke-linecap="round" stroke-linejoin="round">
          <circle cx="18" cy="42" r="7"/>
          <circle cx="46" cy="42" r="7"/>
          <path d="M25 42h10l6-10h8"/>
          <path d="M35 32l-8-8H18"/>
          <path d="M27 24h10l4 8"/>
          <path d="M40 24h6"/>
        </g>
        <circle cx="18" cy="42" r="2.5" fill="#2563eb"/>
        <circle cx="46" cy="42" r="2.5" fill="#2563eb"/>
      </svg>
    </div>
  `,
  iconSize: [44, 44],
  iconAnchor: [22, 22],
  popupAnchor: [0, -20],
});

const pickupIcon = L.divIcon({
  className: 'pickup-marker',
  html: `
    <div class="pin-badge pin-pickup">
      <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="#fff" stroke-width="2.2">
        <path d="M12 21s7-5.2 7-11a7 7 0 1 0-14 0c0 5.8 7 11 7 11z"/>
        <circle cx="12" cy="10" r="2.5" fill="#fff" stroke="none"/>
      </svg>
    </div>
  `,
  iconSize: [36, 36],
  iconAnchor: [18, 34],
  popupAnchor: [0, -28],
});

function FitBoundsOnce({ pickup, courier, routePositions }) {
  const map = useMap();
  const fitted = useRef(false);

  useEffect(() => {
    if (fitted.current) return;
    if (routePositions?.length > 1) {
      map.fitBounds(routePositions, { padding: [48, 48], maxZoom: 14, animate: true });
      fitted.current = true;
      return;
    }
    const points = [courier, pickup].filter((p) => p?.[0] != null && p?.[1] != null);
    if (points.length >= 2) {
      map.fitBounds(points, { padding: [56, 56], maxZoom: 15, animate: true });
      fitted.current = true;
    } else if (points.length === 1) {
      map.setView(points[0], 14);
      fitted.current = true;
    }
  }, [pickup, courier, routePositions, map]);

  return null;
}

function RecenterCourier({ lat, lng, follow }) {
  const map = useMap();
  useEffect(() => {
    if (follow && lat != null && lng != null) {
      map.panTo([lat, lng], { animate: true, duration: 0.9 });
    }
  }, [lat, lng, follow, map]);
  return null;
}

const LiveMap = ({
  courierLocation,
  courierName,
  pickup,
  distanceLabel,
  etaLabel,
  live,
  routePositions = [],
  snappedCourier = null,
}) => {
  const defaultCenter = [41.0082, 28.9784];
  const pickupPos =
    pickup?.[0] != null && pickup?.[1] != null ? [pickup[0], pickup[1]] : null;

  // Prefer road-snapped point so the moto isn't drawn in the water
  const currentPos = snappedCourier
    ? snappedCourier
    : courierLocation
      ? [courierLocation.lat, courierLocation.lng]
      : null;

  const center = currentPos || pickupPos || defaultCenter;

  return (
    <div className="relative h-full w-full">
      <MapContainer center={center} zoom={13} style={{ height: '100%', width: '100%' }} zoomControl={false}>
        <TileLayer
          url="https://tiles.stadiamaps.com/tiles/alidade_smooth/{z}/{x}/{y}{r}.png"
          attribution='&copy; <a href="https://stadiamaps.com/">Stadia Maps</a> &copy; <a href="https://openmaptiles.org/">OpenMapTiles</a> &copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
        />

        {pickupPos && (
          <Marker position={pickupPos} icon={pickupIcon}>
            <Popup>
              <div className="text-gray-900 font-semibold text-sm">Alış noktası</div>
            </Popup>
          </Marker>
        )}

        {currentPos && (
          <Marker position={currentPos} icon={motoIcon}>
            <Popup>
              <div className="text-gray-900 font-semibold text-sm">{courierName || 'Kurye'}</div>
              <div className="text-xs text-sky-600 font-bold">Motosiklet · CANLI</div>
            </Popup>
          </Marker>
        )}

        {routePositions.length > 1 && (
          <Polyline
            positions={routePositions}
            pathOptions={{
              color: '#2563eb',
              weight: 4,
              opacity: 0.9,
              lineCap: 'round',
              lineJoin: 'round',
            }}
          />
        )}

        <FitBoundsOnce pickup={pickupPos} courier={currentPos} routePositions={routePositions} />
        {currentPos && (
          <RecenterCourier lat={currentPos[0]} lng={currentPos[1]} follow={live} />
        )}
      </MapContainer>

      <div className="pointer-events-none absolute left-4 top-4 z-[500] flex flex-col gap-2">
        <div
          className={`pointer-events-auto inline-flex items-center gap-2 self-start rounded-lg border bg-white/95 px-3 py-2 text-xs font-semibold shadow-[0_6px_20px_rgba(15,23,42,0.10)] backdrop-blur-sm ${
            live ? 'border-emerald-200 text-emerald-700' : 'border-amber-200 text-amber-700'
          }`}
        >
          <span className={`h-2 w-2 rounded-full ${live ? 'bg-emerald-500' : 'bg-amber-400'}`} />
          {live ? 'Canlı takip' : 'Bağlantı bekleniyor'}
        </div>
        {(distanceLabel || etaLabel) && (
          <div className="pointer-events-auto min-w-[158px] rounded-xl border border-slate-200 bg-white/95 px-4 py-3 text-slate-800 shadow-[0_8px_28px_rgba(15,23,42,0.10)] backdrop-blur-sm">
            <p className="text-[10px] uppercase tracking-wider text-slate-500 font-semibold">Alışa kalan</p>
            <p className="mt-0.5 text-xl font-semibold leading-tight tabular-nums">{distanceLabel || '—'}</p>
            <p className="text-sm text-slate-600 mt-1">
              Tahmini varış <span className="font-semibold text-blue-700">{etaLabel || '—'}</span>
            </p>
          </div>
        )}
      </div>

      {courierLocation && (
        <div className="pointer-events-none absolute bottom-4 left-4 z-[500] rounded-lg border border-slate-200 bg-white/95 px-3 py-2 text-xs text-slate-700 shadow-[0_6px_20px_rgba(15,23,42,0.10)] backdrop-blur-sm">
          <span className="text-slate-500">Konum</span>{' '}
          <span className="font-mono font-medium tabular-nums text-slate-800">
            {courierLocation.lat.toFixed(5)}, {courierLocation.lng.toFixed(5)}
          </span>
        </div>
      )}
    </div>
  );
};

export default LiveMap;
