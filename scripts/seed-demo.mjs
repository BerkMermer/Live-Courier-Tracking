/**
 * UTF-8-safe demo seed for local K8s / Compose UI.
 * node scripts/seed-demo.mjs
 */
const baseUrl = process.env.DEMO_URL || 'http://127.0.0.1:18080';

async function json(method, path, body, token) {
  const res = await fetch(`${baseUrl}${path}`, {
    method,
    headers: {
      'Content-Type': 'application/json; charset=utf-8',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let data = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = { raw: text };
  }
  if (!res.ok) {
    throw new Error(`${method} ${path} -> ${res.status} ${text}`);
  }
  return data;
}

async function loginOrRegister(kind) {
  if (kind === 'customer') {
    try {
      return await json('POST', '/api/v1/auth/login', {
        email: 'berk.mermer@example.com',
        password: 'securePass123',
      });
    } catch {
      return json('POST', '/api/v1/auth/register', {
        fullName: 'Berk Coşkun Mermer',
        email: 'berk.mermer@example.com',
        phoneNumber: '+905551000001',
        password: 'securePass123',
      });
    }
  }
  try {
    return await json('POST', '/api/v1/auth/login', {
      email: 'emre.kaya@example.com',
      password: 'securePass123',
    });
  } catch {
    return json('POST', '/api/v1/auth/register-courier', {
      fullName: 'Emre Kaya',
      email: 'emre.kaya@example.com',
      phoneNumber: '+905551000002',
      password: 'securePass123',
      vehiclePlate: '34 BKM 2026',
    });
  }
}

async function main() {
  const customer = await loginOrRegister('customer');
  const courier = await loginOrRegister('courier');

  // Fix names if previously corrupted by PowerShell encoding
  // (login works; display name comes from DB — recreate order with correct UTF-8 addresses)

  const start = { lat: 40.9755, lng: 29.055 };
  const pickup = { lat: 40.9901, lng: 29.0292 };

  await json(
    'PUT',
    '/api/v1/couriers/location',
    { latitude: start.lat, longitude: start.lng },
    courier.token
  );

  const order = await json(
    'POST',
    '/api/v1/orders',
    {
      pickupAddress: 'Kadıköy, İstanbul',
      pickupLatitude: pickup.lat,
      pickupLongitude: pickup.lng,
      deliveryAddress: 'Beşiktaş, İstanbul',
    },
    customer.token
  );

  const assigned = await json(
    'POST',
    `/api/v1/orders/${order.id}/assign-courier`,
    {},
    courier.token
  );

  const osrm = await fetch(
    `https://router.project-osrm.org/route/v1/driving/${start.lng},${start.lat};${pickup.lng},${pickup.lat}?overview=full&geometries=geojson`
  ).then((r) => r.json());
  const coords = osrm.routes?.[0]?.geometry?.coordinates;
  if (!coords?.length) throw new Error('OSRM empty');

  const step = Math.max(1, Math.floor(coords.length / 16));
  const points = [];
  for (let i = 0; i < coords.length; i += step) {
    points.push([coords[i][1], coords[i][0]]);
  }
  // Stop ~40% along the route so screenshots show a visible blue path
  const midIdx = Math.floor(points.length * 0.35);
  for (let i = 0; i <= midIdx; i += 1) {
    const [lat, lng] = points[i];
    await json(
      'PUT',
      '/api/v1/couriers/location',
      { latitude: lat, longitude: lng },
      courier.token
    );
    await new Promise((r) => setTimeout(r, 250));
  }

  // Try fix customer display name via register is impossible; patch through SQL if needed
  console.log(
    JSON.stringify(
      {
        customer: customer.user,
        courier: courier.user,
        orderId: order.id,
        tracking: order.trackingNumber,
        status: assigned.status,
        login: {
          email: 'berk.mermer@example.com',
          password: 'securePass123',
        },
      },
      null,
      2
    )
  );
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
