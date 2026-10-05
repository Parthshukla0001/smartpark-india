import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { parkingApi } from '../../api';
import { ParkingLot } from '../../types';
import { Card } from '../../components/common/Card';
import { Button } from '../../components/common/Button';
import { Input } from '../../components/common/Input';
import { Badge } from '../../components/common/Badge';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';
import { EmptyState } from '../../components/common/EmptyState';
import { Search, MapPin, Clock, Layers, ArrowRight, Building } from 'lucide-react';

const POPULAR_CITIES = ['All Cities', 'Bengaluru', 'Mumbai', 'Delhi', 'Pune', 'Hyderabad', 'Chennai'];

export const ParkingSearchPage: React.FC = () => {
  const navigate = useNavigate();

  const [lots, setLots] = useState<ParkingLot[]>([]);
  const [selectedCity, setSelectedCity] = useState<string>('All Cities');
  const [searchTerm, setSearchTerm] = useState<string>('');
  const [loading, setLoading] = useState<boolean>(true);

  const fetchLots = async (city?: string) => {
    try {
      setLoading(true);
      const params: any = { page: 0, size: 50 };
      if (city && city !== 'All Cities') {
        params.city = city;
      }
      const res = await parkingApi.getParkingLots(params);
      setLots(res.content);
    } catch (err) {
      console.error('Failed to load lots:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchLots(selectedCity);
  }, [selectedCity]);

  const filteredLots = lots.filter((lot) => {
    if (!searchTerm.trim()) return true;
    const term = searchTerm.toLowerCase();
    return (
      lot.name.toLowerCase().includes(term) ||
      lot.city.toLowerCase().includes(term) ||
      lot.address.toLowerCase().includes(term) ||
      lot.code.toLowerCase().includes(term)
    );
  });

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '1.75rem' }}>
      <div>
        <h1 style={{ fontSize: '1.65rem', fontWeight: 700, color: '#F8FAFC' }}>
          Find Parking Spaces
        </h1>
        <p style={{ fontSize: '0.9rem', color: '#94A3B8', marginTop: '4px' }}>
          Browse authorized multi-level facilities and smart parking hubs across India.
        </p>
      </div>

      {/* Filter and Search Bar */}
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          gap: '1rem',
          backgroundColor: '#131B2E',
          padding: '1.25rem',
          borderRadius: '12px',
          border: '1px solid rgba(255, 255, 255, 0.08)',
        }}
      >
        <div style={{ display: 'flex', gap: '1rem', alignItems: 'center' }}>
          <div style={{ flex: 1 }}>
            <Input
              placeholder="Search by facility name, area, or landmark..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              leftIcon={<Search size={18} />}
            />
          </div>
        </div>

        {/* City Filter Pills */}
        <div style={{ display: 'flex', gap: '8px', overflowX: 'auto', paddingBottom: '4px' }}>
          {POPULAR_CITIES.map((city) => (
            <button
              key={city}
              onClick={() => setSelectedCity(city)}
              style={{
                padding: '6px 14px',
                borderRadius: '9999px',
                border:
                  selectedCity === city
                    ? '1px solid #F59E0B'
                    : '1px solid rgba(255, 255, 255, 0.1)',
                backgroundColor:
                  selectedCity === city ? 'rgba(245, 158, 11, 0.15)' : '#1E293B',
                color: selectedCity === city ? '#F59E0B' : '#94A3B8',
                fontSize: '0.825rem',
                fontWeight: 600,
                cursor: 'pointer',
                transition: 'all 0.15s ease',
                whiteSpace: 'nowrap',
              }}
            >
              {city}
            </button>
          ))}
        </div>
      </div>

      {/* Facility Grid */}
      {loading ? (
        <LoadingSpinner message="Searching parking facilities..." />
      ) : filteredLots.length === 0 ? (
        <EmptyState
          icon={<Building size={28} />}
          title="No Parking Lots Found"
          description={`No parking facilities matching "${searchTerm || selectedCity}". Try changing your search or selecting another city.`}
        />
      ) : (
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fill, minmax(340px, 1fr))',
            gap: '1.25rem',
          }}
        >
          {filteredLots.map((lot) => (
            <Card
              key={lot.id}
              onClick={() => navigate(`/driver/lots/${lot.id}`)}
              style={{ cursor: 'pointer' }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <div>
                  <h3 style={{ fontSize: '1.15rem', fontWeight: 600, color: '#F8FAFC' }}>
                    {lot.name}
                  </h3>
                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: '4px',
                      color: '#CBD5E1',
                      fontSize: '0.85rem',
                      marginTop: '4px',
                    }}
                  >
                    <MapPin size={14} color="#F59E0B" /> {lot.address}, {lot.city}
                  </div>
                </div>
                <Badge variant={lot.active ? 'success' : 'neutral'}>
                  {lot.active ? 'OPEN' : 'CLOSED'}
                </Badge>
              </div>

              <div
                style={{
                  marginTop: '1rem',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '1.25rem',
                  fontSize: '0.825rem',
                  color: '#94A3B8',
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                  <Clock size={14} />
                  <span>
                    {lot.openingTime?.substring(0, 5)} - {lot.closingTime?.substring(0, 5)}
                  </span>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                  <Layers size={14} />
                  <span>{lot.totalFloors} Floors ({lot.totalCapacity} Slots)</span>
                </div>
              </div>

              <div
                style={{
                  marginTop: '1.25rem',
                  paddingTop: '1rem',
                  borderTop: '1px solid rgba(255, 255, 255, 0.06)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                }}
              >
                <div>
                  <div style={{ fontSize: '0.75rem', color: '#94A3B8' }}>Hourly Rate</div>
                  <div style={{ fontSize: '1.2rem', fontWeight: 700, color: '#F59E0B' }}>
                    ₹{lot.hourlyRate}
                    <span style={{ fontSize: '0.75rem', fontWeight: 400, color: '#94A3B8' }}>
                      /hr
                    </span>
                  </div>
                </div>

                <Button variant="primary" size="sm" icon={<ArrowRight size={14} />}>
                  View & Reserve Slots
                </Button>
              </div>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
};
