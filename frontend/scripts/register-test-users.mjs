const API_BASE_URL = 'http://localhost:8080/api'

const names = [
  'Minh Anh',
  'Gia Bao',
  'Thanh Binh',
  'Quoc Cuong',
  'Hoang Duy',
  'Tuan Kiet',
  'Bao Long',
  'Duc Minh',
  'Ngoc Han',
  'Phuong Linh',
  'Thu Ha',
  'Mai Anh',
  'Khanh Ly',
  'Nhat Nam',
  'Quang Huy',
  'Thanh Son',
  'Anh Thu',
  'Bao Ngoc',
  'Hoai Nam',
  'Minh Quan',
]

const users = names.map((displayName, index) => {
  const n = String(index + 1).padStart(2, '0')

  return {
    displayName,
    email: `testuser${n}@synchat.local`,
    password: '12345678',
  }
})

for (const user of users) {
  const response = await fetch(`${API_BASE_URL}/auth/register`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(user),
  })

  if (!response.ok) {
    console.log(`FAILED ${user.email}: ${response.status} ${await response.text()}`)
    continue
  }

  console.log(`OK ${user.displayName} <${user.email}>`)
}