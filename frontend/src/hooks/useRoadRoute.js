import { useEffect, useRef, useState } from 'react';

const OSRM_ROUTE = 'https://router.project-osrm.org/route/v1/driving';

/**
 * Fetches a driving route that follows roads/bridges (not straight across water).
 */
export function useRoadRoute(fromLat, fromLng, toLat, toLng) {
  const [route, setRoute] = useState({
    positions: [],
    distanceKm: null,
    durationMin: null,
    snappedFrom: null,
  });
  const reqIdRef = useRef(0);

  useEffect(() => {
    if (
      [fromLat, fromLng, toLat, toLng].some(
        (v) => v == null || Number.isNaN(Number(v))
      )
    ) {
      setRoute({ positions: [], distanceKm: null, durationMin: null, snappedFrom: null });
      return undefined;
    }

    const reqId = ++reqIdRef.current;
    const timer = setTimeout(async () => {
      try {
        const url =
          `${OSRM_ROUTE}/` +
          `${Number(fromLng)},${Number(fromLat)};${Number(toLng)},${Number(toLat)}` +
          `?overview=full&geometries=geojson`;

        const res = await fetch(url);
        if (!res.ok) throw new Error(`OSRM ${res.status}`);
        const data = await res.json();
        if (reqId !== reqIdRef.current) return;

        const best = data.routes?.[0];
        const coords = best?.geometry?.coordinates;
        if (!coords?.length) throw new Error('No route');

        const positions = coords.map(([lng, lat]) => [lat, lng]);
        setRoute({
          positions,
          distanceKm: best.distance / 1000,
          durationMin: Math.max(1, Math.round(best.duration / 60)),
          // First vertex is road-snapped — keeps marker off the water
          snappedFrom: positions[0],
        });
      } catch (err) {
        if (reqId !== reqIdRef.current) return;
        console.warn('Yol rotası alınamadı:', err.message);
      }
    }, 400);

    return () => {
      clearTimeout(timer);
      reqIdRef.current += 1;
    };
  }, [fromLat, fromLng, toLat, toLng]);

  return route;
}
