export function formatKm(km) {
  if (km == null) return '—';
  if (km < 1) return `${Math.round(km * 1000)} m`;
  return `${km.toFixed(1)} km`;
}

/** Backend LocalDateTime has no zone; this app stores Europe/Istanbul wall clock. */
export function parseApiDate(value) {
  if (value == null || value === '') return null;
  if (value instanceof Date) return Number.isNaN(value.getTime()) ? null : value;
  if (typeof value === 'number') {
    const d = new Date(value);
    return Number.isNaN(d.getTime()) ? null : d;
  }
  const s = String(value).trim();
  if (/^\d{4}-\d{2}-\d{2}T/.test(s) && !/(Z|[+-]\d{2}:?\d{2})$/i.test(s)) {
    const d = new Date(`${s}+03:00`);
    return Number.isNaN(d.getTime()) ? null : d;
  }
  const d = new Date(s);
  return Number.isNaN(d.getTime()) ? null : d;
}

export function formatApiTimestamp(value) {
  const date = parseApiDate(value);
  if (!date) return '—';
  return new Intl.DateTimeFormat('tr-TR', {
    timeZone: 'Europe/Istanbul',
    day: 'numeric',
    month: 'long',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date);
}

/**
 * Project a GPS point onto the nearest segment of a lat/lng polyline
 * so the marker sits on the drawn route instead of a parallel street.
 */
export function nearestPointOnPolyline(lat, lng, positions) {
  if (lat == null || lng == null || !positions?.length) return null;
  if (positions.length === 1) return positions[0];

  let best = positions[0];
  let bestDist = Infinity;

  for (let i = 0; i < positions.length - 1; i += 1) {
    const a = positions[i];
    const b = positions[i + 1];
    const projected = projectOnSegment(lat, lng, a[0], a[1], b[0], b[1]);
    const d = squaredDistance(lat, lng, projected[0], projected[1]);
    if (d < bestDist) {
      bestDist = d;
      best = projected;
    }
  }

  return best;
}

function projectOnSegment(lat, lng, lat1, lng1, lat2, lng2) {
  const x = lng;
  const y = lat;
  const x1 = lng1;
  const y1 = lat1;
  const x2 = lng2;
  const y2 = lat2;
  const dx = x2 - x1;
  const dy = y2 - y1;
  if (dx === 0 && dy === 0) return [lat1, lng1];
  const t = Math.max(0, Math.min(1, ((x - x1) * dx + (y - y1) * dy) / (dx * dx + dy * dy)));
  return [y1 + t * dy, x1 + t * dx];
}

function squaredDistance(lat1, lng1, lat2, lng2) {
  const dLat = lat1 - lat2;
  const dLng = lng1 - lng2;
  return dLat * dLat + dLng * dLng;
}
