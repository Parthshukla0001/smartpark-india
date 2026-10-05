export class ApiError extends Error {
  status: number;
  data: any;

  constructor(message: string, status: number, data?: any) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.data = data;
  }
}

function generateCorrelationId(): string {
  return 'web-' + Math.random().toString(36).substring(2, 10) + '-' + Date.now();
}

const BASE_URL = '';

async function request<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
  const token = localStorage.getItem('smartpark_token');
  const headers = new Headers(options.headers || {});

  if (!headers.has('Content-Type') && !(options.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json');
  }

  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  headers.set('X-Correlation-ID', generateCorrelationId());

  const config: RequestInit = {
    ...options,
    headers,
  };

  const url = `${BASE_URL}${endpoint}`;
  let response: Response;
  try {
    response = await fetch(url, config);
  } catch (err: any) {
    throw new ApiError(err?.message || 'Network error, please check connection', 0);
  }

  if (response.status === 204) {
    return {} as T;
  }

  const contentType = response.headers.get('content-type');
  let data: any = null;
  if (contentType && contentType.includes('application/json')) {
    try {
      data = await response.json();
    } catch {
      data = null;
    }
  } else {
    data = await response.text();
  }

  if (!response.ok) {
    let message = 'An error occurred';
    if (data && typeof data === 'object') {
      message = data.message || data.error || JSON.stringify(data);
    } else if (typeof data === 'string' && data.length > 0) {
      message = data;
    }

    if (response.status === 401) {
      // Token might be expired or invalid
      // If not on login page, we can let caller handle or clear
      if (!window.location.pathname.includes('/login')) {
        localStorage.removeItem('smartpark_token');
        localStorage.removeItem('smartpark_user');
      }
    }

    throw new ApiError(message, response.status, data);
  }

  return data as T;
}

export const apiClient = {
  get<T>(endpoint: string, options?: RequestInit): Promise<T> {
    return request<T>(endpoint, { ...options, method: 'GET' });
  },
  post<T>(endpoint: string, body?: any, options?: RequestInit): Promise<T> {
    return request<T>(endpoint, {
      ...options,
      method: 'POST',
      body: body ? JSON.stringify(body) : undefined,
    });
  },
  put<T>(endpoint: string, body?: any, options?: RequestInit): Promise<T> {
    return request<T>(endpoint, {
      ...options,
      method: 'PUT',
      body: body ? JSON.stringify(body) : undefined,
    });
  },
  patch<T>(endpoint: string, body?: any, options?: RequestInit): Promise<T> {
    return request<T>(endpoint, {
      ...options,
      method: 'PATCH',
      body: body ? JSON.stringify(body) : undefined,
    });
  },
  delete<T>(endpoint: string, options?: RequestInit): Promise<T> {
    return request<T>(endpoint, { ...options, method: 'DELETE' });
  },
};
