import axios from 'axios'

const api = axios.create({
  baseURL: 'http://localhost:8080/api/v1',
  headers: {
    'Content-Type': 'application/json',
    'X-Dev-User-Email': 'admin@local',
    'X-Dev-User-Role': 'AGENCY_ADMIN',
    'X-Agency-Id': '00000000-0000-0000-0000-000000000001',
  }
})

export default api
