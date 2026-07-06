import http from 'k6/http';
import { check, sleep } from 'k6';

export let options = {
  stages: [
    { duration: '30s', target: 100 },
    { duration: '1m', target: 100 },
    { duration: '30s', target: 0 },
  ],
};

export default function () {
  // Test login
  let loginRes = http.post('http://localhost:8080/api/v1/auth/login', JSON.stringify({
    username: 'admin',
    password: 'password'
  }), {
    headers: { 'Content-Type': 'application/json' }
  });
  
  check(loginRes, { 'login status 200': (r) => r.status === 200 });
  
  if (loginRes.status === 200) {
    let token = JSON.parse(loginRes.body).accessToken;
    
    // Test dashboard
    let dashboardRes = http.get('http://localhost:8080/api/v1/dashboard/summary', {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    check(dashboardRes, { 'dashboard status 200': (r) => r.status === 200 });
  }
  
  sleep(1);
}
