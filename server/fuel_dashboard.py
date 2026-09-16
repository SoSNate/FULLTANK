import os
import json
from datetime import datetime, date
# Load environment variables if .env exists
env_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), '.env')
if os.path.exists(env_path):
    with open(env_path, 'r', encoding='utf-8') as ef:
        for line in ef:
            line = line.strip()
            if line and not line.startswith('#') and '=' in line:
                k, v = line.split('=', 1)
                os.environ.setdefault(k.strip(), v.strip())

import google_tools
from googleapiclient.discovery import build

SPREADSHEET_ID = os.environ.get('SPREADSHEET_ID', 'your_google_sheet_id_here')
CAR_STATE_FILE = os.environ.get('CAR_STATE_FILE', os.path.join(os.path.dirname(os.path.abspath(__file__)), 'car_state.json'))

DEFAULT_STATE = {
    'active_plate': '12-345-67',
    'vehicles': [
        {
            'plate': '12-345-67',
            'car_model': 'רכב לדוגמה (שנה)',
            'tank_capacity': 55.0,
            'full_tank_refuel_km': 150000,
            'current_odometer': 150000,
            'avg_km_per_liter': 14.0,
            'city_km_per_liter': 8.0,
            'test_expiry_date': '2027-01-01',
            'tire_size': '195/65R15',
            'last_full_refuel_date': '01/01/2027'
        }
    ],
    'insurance': {
        '12-345-67': {
            'plate': '12-345-67',
            'insurance_type': 'חובה + צד ג'',
            'company': 'חברת ביטוח לדוגמה',
            'start_date': '2027-01-01',
            'end_date': '2028-01-01',
            'annual_cost': 3000.0,
            'monthly_cost': 250.0,
            'policy_number': 'POL-EXAMPLE-12345',
            'notes': 'שירותי דרך וגרירה'
        }
    },
    'maintenance': {
        '12-345-67': []
    }
}

def load_car_state():
    state = dict(DEFAULT_STATE)
    if os.path.exists(CAR_STATE_FILE):
        try:
            with open(CAR_STATE_FILE, 'r', encoding='utf-8') as f:
                saved = json.load(f)
                # Handle migration from legacy flat format
                if 'vehicles' not in saved and 'plate' in saved:
                    legacy_plate = saved.get('plate', '86-369-79')
                    state['active_plate'] = legacy_plate
                    state['vehicles'] = [{
                        'plate': legacy_plate,
                        'car_model': saved.get('car_model', 'טויוטה קורולה GLI 2012'),
                        'tank_capacity': float(saved.get('tank_capacity', 50.0)),
                        'full_tank_refuel_km': int(saved.get('full_tank_refuel_km', 162024)),
                        'current_odometer': int(saved.get('current_odometer', 162024)),
                        'avg_km_per_liter': float(saved.get('avg_km_per_liter', 14.24)),
                        'city_km_per_liter': float(saved.get('city_km_per_liter', 7.90)),
                        'test_expiry_date': saved.get('test_expiry_date', '2026-12-24'),
                        'tire_size': saved.get('tire_size', '195/65R15'),
                        'last_full_refuel_date': saved.get('last_full_refuel_date', '10/09/2026')
                    }]
                else:
                    state.update(saved)
        except Exception:
            pass
    return state

def save_car_state(state):
    try:
        with open(CAR_STATE_FILE, 'w', encoding='utf-8') as f:
            json.dump(state, f, ensure_ascii=False, indent=2)
        return True
    except Exception:
        return False

def get_active_vehicle(state=None):
    if state is None:
        state = load_car_state()
    active_plate = state.get('active_plate', '86-369-79')
    vehicles = state.get('vehicles', [])
    for v in vehicles:
        if v.get('plate') == active_plate:
            return v
    if vehicles:
        return vehicles[0]
    return DEFAULT_STATE['vehicles'][0]

def set_active_vehicle(plate):
    state = load_car_state()
    plate = plate.strip()
    found = False
    for v in state.get('vehicles', []):
        if v.get('plate') == plate:
            found = True
            break
    if not found:
        # Create new vehicle entry
        state.setdefault('vehicles', []).append({
            'plate': plate,
            'car_model': f'רכב {plate}',
            'tank_capacity': 50.0,
            'full_tank_refuel_km': 0,
            'current_odometer': 0,
            'avg_km_per_liter': 14.24,
            'city_km_per_liter': 7.90,
            'test_expiry_date': '2026-12-24',
            'tire_size': '195/65R15',
            'last_full_refuel_date': datetime.now().strftime('%d/%m/%Y')
        })
    state['active_plate'] = plate
    save_car_state(state)
    return state

def save_vehicle_details(vehicle_data):
    state = load_car_state()
    plate = str(vehicle_data.get('plate', '')).strip()
    if not plate:
        return False, "Missing plate"
    
    vehicles = state.setdefault('vehicles', [])
    updated = False
    for idx, v in enumerate(vehicles):
        if v.get('plate') == plate:
            vehicles[idx].update(vehicle_data)
            updated = True
            break
            
    if not updated:
        vehicles.append(vehicle_data)
        
    if vehicle_data.get('is_active', False):
        state['active_plate'] = plate
        
    save_car_state(state)
    return True, state

def save_insurance_record(ins_data):
    state = load_car_state()
    plate = str(ins_data.get('plate', state.get('active_plate', '86-369-79'))).strip()
    annual_cost = float(ins_data.get('annual_cost', 0.0))
    monthly_cost = round(annual_cost / 12.0, 2) if annual_cost > 0 else 0.0
    
    record = {
        'plate': plate,
        'insurance_type': ins_data.get('insurance_type', 'מקיף + חובה'),
        'company': ins_data.get('company', ''),
        'start_date': ins_data.get('start_date', ''),
        'end_date': ins_data.get('end_date', ''),
        'annual_cost': annual_cost,
        'monthly_cost': monthly_cost,
        'policy_number': ins_data.get('policy_number', ''),
        'notes': ins_data.get('notes', '')
    }
    
    state.setdefault('insurance', {})[plate] = record
    save_car_state(state)
    return True, record

def save_maintenance_record(maint_data):
    state = load_car_state()
    plate = str(maint_data.get('plate', state.get('active_plate', '86-369-79'))).strip()
    
    records = state.setdefault('maintenance', {}).setdefault(plate, [])
    new_id = (max([r.get('id', 0) for r in records], default=0)) + 1
    
    record = {
        'id': new_id,
        'date': maint_data.get('date', datetime.now().strftime('%Y-%m-%d')),
        'km': int(str(maint_data.get('km', 0)).replace(',', '')),
        'service_type': maint_data.get('service_type', 'תחזוקה שוטפת'),
        'description': maint_data.get('description', ''),
        'cost': float(maint_data.get('cost', 0.0)),
        'garage': maint_data.get('garage', ''),
        'next_service_km': int(str(maint_data.get('next_service_km', 0)).replace(',', '')),
        'receipt_url': maint_data.get('receipt_url', ''),
        'parts_detail': maint_data.get('parts_detail', ''),
        'category': maint_data.get('category', 'תחזוקה שוטפת')
    }
    
    records.insert(0, record)
    save_car_state(state)
    return True, record

def get_fuel_data():
    try:
        state = load_car_state()
        active_veh = get_active_vehicle(state)
        active_plate = active_veh.get('plate', '86-369-79')
        
        creds = google_tools.get_credentials()
        service = build('sheets', 'v4', credentials=creds)
        
        # Get 2026 rows
        res_2026 = service.spreadsheets().values().get(
            spreadsheetId=SPREADSHEET_ID,
            range='תדלוקים 2026!A2:H60'
        ).execute()
        rows_2026 = res_2026.get('values', [])
        
        entries_2026 = []
        total_spent_2026 = 0.0
        total_liters_2026 = 0.0
        latest_km = int(active_veh.get('current_odometer', 162024))
        
        monthly_spend = {m: 0.0 for m in range(1, 13)}
        monthly_liters = {m: 0.0 for m in range(1, 13)}
        
        for r in rows_2026:
            if not r or not r[0]:
                continue
            date_str = r[0]
            km_raw = r[1].replace(',', '').replace(' ', '') if len(r) > 1 and r[1] else ''
            total_val = float(r[2].replace(',', '')) if len(r) > 2 and r[2] else 0.0
            liters_val = float(r[3].replace(',', '')) if len(r) > 3 and r[3] else 0.0
            rate_val = float(r[4].replace(',', '')) if len(r) > 4 and r[4] else 0.0
            station_val = r[5] if len(r) > 5 else 'סונול'
            receipt_url = r[6] if len(r) > 6 else ''
            pdf_url = r[7] if len(r) > 7 else ''
            
            km_val = int(km_raw) if km_raw.isdigit() else None
            if km_val and km_val > latest_km:
                latest_km = km_val
                
            total_spent_2026 += total_val
            total_liters_2026 += liters_val
            
            try:
                dt = datetime.strptime(date_str, "%d/%m/%Y")
                monthly_spend[dt.month] += total_val
                monthly_liters[dt.month] += liters_val
            except Exception:
                pass
                
            entries_2026.append({
                'date': date_str,
                'km': km_val,
                'total': total_val,
                'liters': liters_val,
                'rate': rate_val,
                'station': station_val,
                'url': receipt_url,
                'pdf': pdf_url
            })
            
        if latest_km > int(active_veh.get('current_odometer', 162024)):
            active_veh['current_odometer'] = latest_km
            save_car_state(state)

        avg_km_per_liter = float(active_veh.get('avg_km_per_liter', 14.24))
        city_km_per_liter = float(active_veh.get('city_km_per_liter', 7.90))
        
        avg_rate = (total_spent_2026 / total_liters_2026) if total_liters_2026 > 0 else 7.75
        fuel_cost_per_km = round(avg_rate / avg_km_per_liter, 2)
            
        next_service_km = 163000
        service_remaining_km = max(0, next_service_km - latest_km)

        tank_capacity = float(active_veh.get('tank_capacity', 50.0))
        full_tank_refuel_km = int(active_veh.get('full_tank_refuel_km', 162024))
        
        km_driven_since_full = max(0, latest_km - full_tank_refuel_km)
        fuel_consumed_since_full = km_driven_since_full / avg_km_per_liter
        current_fuel_liters = max(0.0, min(tank_capacity, tank_capacity - fuel_consumed_since_full))
        fuel_percent = round((current_fuel_liters / tank_capacity) * 100, 1)
        estimated_remaining_range = round(current_fuel_liters * avg_km_per_liter)
        city_range = round(current_fuel_liters * city_km_per_liter)
        
        month_names = ['ינואר', 'פברואר', 'מרץ', 'אפריל', 'מאי', 'יוני', 'יולי', 'אוגוסט', 'ספטמבר', 'אוקטובר', 'נובמבר', 'דצמבר']
        chart_months = month_names[:9]
        chart_spends = [round(monthly_spend[m], 2) for m in range(1, 10)]
        chart_liters = [round(monthly_liters[m], 2) for m in range(1, 10)]
        
        rate_dates = [e['date'] for e in entries_2026]
        rate_values = [e['rate'] for e in entries_2026]
        
        # Insurance & Maintenance & TCO
        ins_record = state.get('insurance', {}).get(active_plate, DEFAULT_STATE['insurance']['86-369-79'])
        maint_records = state.get('maintenance', {}).get(active_plate, DEFAULT_STATE['maintenance']['86-369-79'])
        total_maint_cost = sum([r.get('cost', 0.0) for r in maint_records])
        annual_insurance = float(ins_record.get('annual_cost', 3450.0))
        monthly_insurance = float(ins_record.get('monthly_cost', 287.5))
        
        # Overall TCO (Total Cost of Ownership)
        total_annual_expenses = total_spent_2026 + total_maint_cost + annual_insurance
        
        # Calculate days until insurance expiry
        insurance_days_remaining = 0
        try:
            end_dt = datetime.strptime(ins_record.get('end_date', '2026-12-31'), '%Y-%m-%d').date()
            today = date.today()
            insurance_days_remaining = (end_dt - today).days
        except Exception:
            pass

        # Calculate days until test expiry
        test_days_remaining = 0
        try:
            test_dt = datetime.strptime(active_veh.get('test_expiry_date', '2026-12-24'), '%Y-%m-%d').date()
            test_days_remaining = (test_dt - date.today()).days
        except Exception:
            pass

        return {
            'status': 'success',
            'car': active_veh.get('car_model', 'טויוטה קורולה GLI 2012 ידנית 1.6L'),
            'plate': active_plate,
            'all_vehicles': state.get('vehicles', []),
            'latest_km': latest_km,
            'full_tank_refuel_km': full_tank_refuel_km,
            'last_full_refuel_date': active_veh.get('last_full_refuel_date', '10/09/2026'),
            'tank_capacity': tank_capacity,
            'current_fuel_liters': round(current_fuel_liters, 1),
            'fuel_percent': fuel_percent,
            'km_driven_since_full': km_driven_since_full,
            'estimated_remaining_range': estimated_remaining_range,
            'city_range': city_range,
            'total_spent_2026': round(total_spent_2026, 2),
            'total_liters_2026': round(total_liters_2026, 2),
            'refuel_count_2026': len(entries_2026),
            'avg_km_per_liter': avg_km_per_liter,
            'city_km_per_liter': city_km_per_liter,
            'avg_cost_per_km': fuel_cost_per_km,
            'next_service_km': next_service_km,
            'service_remaining_km': service_remaining_km,
            'monthly_labels': chart_months,
            'monthly_spends': chart_spends,
            'monthly_liters': chart_liters,
            'rate_dates': rate_dates,
            'rate_values': rate_values,
            'recent_refuels': entries_2026[-6:][::-1],
            'insurance': ins_record,
            'insurance_days_remaining': insurance_days_remaining,
            'maintenance': maint_records,
            'total_maintenance_cost': total_maint_cost,
            'test_expiry_date': active_veh.get('test_expiry_date', '2026-12-24'),
            'test_days_remaining': test_days_remaining,
            'tire_size': active_veh.get('tire_size', '195/65R15'),
            'tco': {
                'total_fuel': round(total_spent_2026, 2),
                'total_maintenance': round(total_maint_cost, 2),
                'annual_insurance': round(annual_insurance, 2),
                'monthly_insurance': round(monthly_insurance, 2),
                'total_annual_expenses': round(total_annual_expenses, 2),
                'total_cost_per_km': round(fuel_cost_per_km + (total_maint_cost + annual_insurance) / max(1, latest_km - full_tank_refuel_km + 10000), 2)
            }
        }
    except Exception as e:
        return {'status': 'error', 'message': str(e)}
