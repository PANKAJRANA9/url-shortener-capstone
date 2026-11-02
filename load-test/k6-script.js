
import http from 'k6/http';
import { check } from 'k6';
export const options = { vus: 100, duration: '30s' };
export default function() {
  const res = http.get(`http://localhost:8080/${__ENV.SHORT_CODE || 'k3J9pQz'}`);
  check(res, { 'redirect 302': (r) => r.status === 302 });
}
