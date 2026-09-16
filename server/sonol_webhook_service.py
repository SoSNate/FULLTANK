#!/usr/bin/env python3
import os
import sys
import json
import subprocess
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs
from datetime import datetime

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
import sonol_parser
import fuel_dashboard
from googleapiclient.discovery import build

SPREADSHEET_ID = os.environ.get('SPREADSHEET_ID', 'your_google_sheet_id_here')
SECRET_TOKEN = os.environ.get('SECRET_TOKEN', 'demo_secret_token')
APK_PATH = os.environ.get('APK_PATH', os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'android/app/build/outputs/apk/debug/app-debug.apk'))
DASHBOARD_FILE = os.environ.get('DASHBOARD_FILE', os.path.join(os.path.dirname(os.path.abspath(__file__)), 'dashboard.html'))

def append_receipt_to_sheets(receipt):
    creds = google_tools.get_credentials()
    service = build('sheets', 'v4', credentials=creds)
    
    tab_name = f"תדלוקים {receipt['year']}"
    
    # Check if tab exists, else use current year tab
    meta = service.spreadsheets().get(spreadsheetId=SPREADSHEET_ID).execute()
    sheet_names = [s['properties']['title'] for s in meta.get('sheets', [])]
    if tab_name not in sheet_names:
        tab_name = 'תדלוקים 2026'

    # Check for duplicate receipt by URL or doc_id
    res_urls = service.spreadsheets().values().get(
        spreadsheetId=SPREADSHEET_ID,
        range=f"'{tab_name}'!G1:G100"
    ).execute()
    existing_urls = [r[0] for r in res_urls.get('values', []) if r]
    
    target_url = receipt.get('url', '')
    for idx, u in enumerate(existing_urls):
        if target_url and (u == target_url or receipt.get('doc_id', '') in u):
            row_num = idx + 1
            print(f"Receipt {receipt['doc_id']} already exists at row {row_num} of {tab_name}. Skipping append.")
            if receipt.get('km'):
                service.spreadsheets().values().update(
                    spreadsheetId=SPREADSHEET_ID,
                    range=f"'{tab_name}'!B{row_num}",
                    valueInputOption='USER_ENTERED',
                    body={'values': [[receipt['km']]]}
                ).execute()
            return f"'{tab_name}'!A{row_num}:H{row_num}"
        
    row = [
        receipt['date'],
        receipt.get('km', ''),
        f"{receipt['total']:.2f}",
        f"{receipt['liters']:.2f}",
        receipt['rate'],
        receipt['station'],
        receipt['url'],
        receipt['pdf_url']
    ]
    
    res = service.spreadsheets().values().append(
        spreadsheetId=SPREADSHEET_ID,
        range=f"'{tab_name}'!A1",
        valueInputOption='USER_ENTERED',
        body={'values': [row]}
    ).execute()
    
    updated_range = res.get('updates', {}).get('updatedRange', '')
    print(f"Appended receipt {receipt['doc_id']} to {tab_name} at {updated_range}")
    return updated_range

def update_last_km_in_sheets(km_value, tab_name='תדלוקים 2026'):
    creds = google_tools.get_credentials()
    service = build('sheets', 'v4', credentials=creds)
    
    res = service.spreadsheets().values().get(
        spreadsheetId=SPREADSHEET_ID,
        range=f"'{tab_name}'!A1:B100"
    ).execute()
    rows = res.get('values', [])
    if not rows:
        return False, "Tab is empty"
    
    last_row_index = len(rows)
    cell = f"'{tab_name}'!B{last_row_index}"
    
    service.spreadsheets().values().update(
        spreadsheetId=SPREADSHEET_ID,
        range=cell,
        valueInputOption='USER_ENTERED',
        body={'values': [[km_value]]}
    ).execute()
    
    print(f"Updated km to {km_value} at {cell}!")
    return True, cell

def append_odometer_log(km_value, source='אנדרואיד אוטו (מסך רכב)', is_full_tank=False, notes=''):
    """Logs driving odometer check-ins to a dedicated Google Sheets tab 'יומן נסועה וכיול'."""
    try:
        creds = google_tools.get_credentials()
        service = build('sheets', 'v4', credentials=creds)
        
        car_state = fuel_dashboard.load_car_state()
        tank_cap = float(car_state.get('tank_capacity', 50.0))
        full_km = int(car_state.get('full_tank_refuel_km', 162024))
        avg_km_l = float(car_state.get('avg_km_per_liter', 14.24))
        
        km_int = int(str(km_value).replace(',', '').replace(' ', ''))
        km_since_full = max(0, km_int - full_km)
        fuel_consumed = km_since_full / avg_km_l
        current_fuel = max(0.0, min(tank_cap, tank_cap - fuel_consumed))
        fuel_pct = round((current_fuel / tank_cap) * 100, 1)
        est_range = round(current_fuel * avg_km_l)
        
        entry_notes = notes
        if not entry_notes:
            entry_notes = "תדלוק פול - איפוס מד דלק ל-100%" if is_full_tank else f"כיול שוטף ({km_since_full} ק\"מ מפול)"
            
        row_data = [
            datetime.now().strftime('%d/%m/%Y %H:%M'),
            f"{km_int:,}",
            str(km_since_full),
            f"{current_fuel:.1f}",
            f"{fuel_pct}%",
            str(est_range),
            source,
            entry_notes
        ]
        
        service.spreadsheets().values().append(
            spreadsheetId=SPREADSHEET_ID,
            range="'יומן נסועה וכיול'!A:H",
            valueInputOption='USER_ENTERED',
            insertDataOption='INSERT_ROWS',
            body={'values': [row_data]}
        ).execute()
        print(f"Logged odometer check-in: {km_int} km to 'יומן נסועה וכיול'")
        return True
    except Exception as e:
        print(f"Error appending odometer log: {e}")
        return False

class FuelWebhookHandler(BaseHTTPRequestHandler):
    def send_cors_headers(self):
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type, X-Auth-Token')

    def handle_one_request(self):
        try:
            super().handle_one_request()
        except (BrokenPipeError, ConnectionResetError):
            pass

    def do_OPTIONS(self):
        self.send_response(200)
        self.send_cors_headers()
        self.end_headers()

    def do_HEAD(self):
        self.do_GET(head_only=True)

    def do_GET(self, head_only=False):
        parsed = urlparse(self.path)
        qs = parse_qs(parsed.query)

        # 0. App Logo & Favicon: /logo.png or /app_icon.png or /favicon.ico
        if parsed.path in ['/logo.png', '/app_icon.png', '/favicon.ico']:
            logo_path = '/root/spacecampus/personal_assistant/logo_squircle.png'
            if os.path.exists(logo_path):
                self.send_response(200)
                self.send_header('Content-Type', 'image/png')
                self.send_header('Cache-Control', 'public, max-age=86400')
                self.send_cors_headers()
                self.end_headers()
                with open(logo_path, 'rb') as f:
                    self.wfile.write(f.read())
                return

        # 1. Download APK endpoint: /download/fulltank.apk or /download/kinginstaller.apk
        if parsed.path in ['/download/kinginstaller.apk', '/kinginstaller.apk']:
            ki_path = '/root/spacecampus/personal_assistant/KingInstaller.apk'
            if os.path.exists(ki_path):
                self.send_response(200)
                self.send_header('Content-Type', 'application/vnd.android.package-archive')
                self.send_header('Content-Disposition', 'attachment; filename="KingInstaller.apk"')
                self.send_header('Content-Length', str(os.path.getsize(ki_path)))
                self.end_headers()
                with open(ki_path, 'rb') as f:
                    self.wfile.write(f.read())
                return

        if parsed.path in ['/download/fulltank.apk', '/download/full-tank.apk', '/fulltank.apk', '/download/sonol-fuel.apk', '/download/sonol.apk', '/sonol.apk']:
            if os.path.exists(APK_PATH):
                self.send_response(200)
                self.send_header('Content-Type', 'application/vnd.android.package-archive')
                self.send_header('Content-Disposition', 'attachment; filename="FullTank.apk"')
                self.send_header('Content-Length', str(os.path.getsize(APK_PATH)))
                self.end_headers()
                with open(APK_PATH, 'rb') as f:
                    self.wfile.write(f.read())
                return
            else:
                self.send_response(404)
                self.end_headers()
                self.wfile.write(b'APK build not found')
                return

        # 2. Stats JSON API: /api/stats
        if parsed.path == '/api/stats':
            stats = fuel_dashboard.get_fuel_data()
            self.send_response(200)
            self.send_header('Content-Type', 'application/json; charset=utf-8')
            self.send_cors_headers()
            self.end_headers()
            self.wfile.write(json.dumps(stats, ensure_ascii=False).encode('utf-8'))
            return

        # 2.5 Car License Plate Lookup API: /api/car-lookup?plate=1234567
        if parsed.path == '/api/car-lookup':
            plate = qs.get('plate', [''])[0].strip().replace('-', '').replace(' ', '')
            res_data = {'status': 'error', 'message': 'לא הוזן מספר רכב'}
            if plate:
                try:
                    import urllib.request
                    res_id = '053cea08-09bc-40ec-8f7a-156f0677aff3'
                    url = f'https://data.gov.il/api/3/action/datastore_search?resource_id={res_id}&q={plate}'
                    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
                    with urllib.request.urlopen(req, timeout=6) as resp:
                        data = json.loads(resp.read().decode('utf-8'))
                        records = data.get('result', {}).get('records', [])
                        if records:
                            rec = records[0]
                            res_data = {
                                'status': 'success',
                                'plate': str(rec.get('mispar_rechev', plate)),
                                'manufacturer': rec.get('tozeret_nm', ''),
                                'model': rec.get('kinuy_mishari', '') or rec.get('degem_nm', ''),
                                'year': rec.get('shnat_yitzur', ''),
                                'trim': rec.get('ramat_gimur', ''),
                                'color': rec.get('tzeva_rechev', ''),
                                'fuel_type': rec.get('sug_delek_nm', 'בנזין'),
                                'test_date': rec.get('tokef_dt', ''),
                                'last_test': rec.get('mivchan_acharon_dt', ''),
                                'engine': rec.get('degem_manoa', ''),
                                'tires': rec.get('zmig_kidmi', '')
                            }
                        else:
                            res_data = {'status': 'not_found', 'message': 'מספר הרכב לא נמצא במאגר משרד התחבורה'}
                except Exception as e:
                    res_data = {'status': 'error', 'message': f'שגיאה בשליפה: {str(e)}'}
            
            self.send_response(200)
            self.send_header('Content-Type', 'application/json; charset=utf-8')
            self.send_cors_headers()
            self.end_headers()
            self.wfile.write(json.dumps(res_data, ensure_ascii=False).encode('utf-8'))
            return

        # 2.6 Get Car Settings: /api/car-settings
        if parsed.path == '/api/car-settings':
            settings = fuel_dashboard.load_car_state()
            self.send_response(200)
            self.send_header('Content-Type', 'application/json; charset=utf-8')
            self.send_cors_headers()
            self.end_headers()
            self.wfile.write(json.dumps({'status': 'success', 'settings': settings}, ensure_ascii=False).encode('utf-8'))
            return

        # 2.7 Vehicles API: /api/vehicles
        if parsed.path == '/api/vehicles':
            state = fuel_dashboard.load_car_state()
            self.send_response(200)
            self.send_header('Content-Type', 'application/json; charset=utf-8')
            self.send_cors_headers()
            self.end_headers()
            self.wfile.write(json.dumps({
                'status': 'success',
                'active_plate': state.get('active_plate', '86-369-79'),
                'vehicles': state.get('vehicles', [])
            }, ensure_ascii=False).encode('utf-8'))
            return

        # 2.8 Insurance API: /api/insurance?plate=...
        if parsed.path == '/api/insurance':
            state = fuel_dashboard.load_car_state()
            plate = qs.get('plate', [state.get('active_plate', '86-369-79')])[0]
            ins = state.get('insurance', {}).get(plate, {})
            self.send_response(200)
            self.send_header('Content-Type', 'application/json; charset=utf-8')
            self.send_cors_headers()
            self.end_headers()
            self.wfile.write(json.dumps({'status': 'success', 'insurance': ins}, ensure_ascii=False).encode('utf-8'))
            return

        # 2.9 Maintenance API: /api/maintenance?plate=...
        if parsed.path == '/api/maintenance':
            state = fuel_dashboard.load_car_state()
            plate = qs.get('plate', [state.get('active_plate', '86-369-79')])[0]
            maint = state.get('maintenance', {}).get(plate, [])
            self.send_response(200)
            self.send_header('Content-Type', 'application/json; charset=utf-8')
            self.send_cors_headers()
            self.end_headers()
            self.wfile.write(json.dumps({'status': 'success', 'maintenance': maint}, ensure_ascii=False).encode('utf-8'))
            return

        # 2.10 TCO API: /api/tco
        if parsed.path == '/api/tco':
            stats = fuel_dashboard.get_fuel_data()
            self.send_response(200)
            self.send_header('Content-Type', 'application/json; charset=utf-8')
            self.send_cors_headers()
            self.end_headers()
            self.wfile.write(json.dumps({'status': 'success', 'tco': stats.get('tco', {})}, ensure_ascii=False).encode('utf-8'))
            return

        # 3. Web Dashboard UI: /dashboard or / (when Accept contains html or user agent is browser)
        accept_header = self.headers.get('Accept', '')
        if parsed.path == '/dashboard' or 'text/html' in accept_header or parsed.path == '/':
            if os.path.exists(DASHBOARD_FILE):
                with open(DASHBOARD_FILE, 'r', encoding='utf-8') as f:
                    html_content = f.read()
                self.send_response(200)
                self.send_header('Content-Type', 'text/html; charset=utf-8')
                self.send_cors_headers()
                self.end_headers()
                self.wfile.write(html_content.encode('utf-8'))
                return

        # Fallback status
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_cors_headers()
        self.end_headers()
        self.wfile.write(b'{"status": "active", "service": "Sonol Fuel Webhook", "version": "3.1"}')

    def do_POST(self):
        content_length = int(self.headers.get('Content-Length', 0))
        body = self.rfile.read(content_length).decode('utf-8', errors='ignore')
        
        parsed = urlparse(self.path)
        qs = parse_qs(parsed.query)
        auth = self.headers.get('X-Auth-Token', '')
        if not auth and 'token' in qs:
            auth = qs['token'][0]
            
        if auth != SECRET_TOKEN:
            self.send_response(403)
            self.send_cors_headers()
            self.end_headers()
            self.wfile.write(b'Forbidden: Invalid auth token')
            return
            
        # 1. Update KM / Calibrate Tank endpoint: /api/update-km
        if parsed.path == '/api/update-km':
            try:
                data = json.loads(body)
                km = str(data.get('km', '')).strip().replace(',', '')
                if not km:
                    self.send_response(400)
                    self.send_cors_headers()
                    self.end_headers()
                    self.wfile.write(b'{"status": "error", "message": "Missing km"}')
                    return
                    
                # Update persistent car state
                km_int = int(km)
                car_state = fuel_dashboard.load_car_state()
                car_state['current_odometer'] = km_int
                is_full = data.get('is_full_tank', False)
                if is_full:
                    car_state['full_tank_refuel_km'] = km_int
                    car_state['last_full_refuel_date'] = datetime.now().strftime('%d/%m/%Y')
                fuel_dashboard.save_car_state(car_state)
                
                # 1. Always append driving odometer check-in to dedicated sheet: 'יומן נסועה וכיול'
                source = data.get('source', 'אנדרואיד אוטו (מסך רכב)')
                notes = data.get('notes', '')
                append_odometer_log(km_int, source=source, is_full_tank=is_full, notes=notes)
                
                # 2. ONLY update the refuel table 'תדלוקים 2026' if explicitly a refuel event
                is_refuel_event = data.get('is_refuel_event', False) or data.get('is_refuel', False)
                updated_cell = 'יומן נסועה וכיול'
                if is_refuel_event:
                    try:
                        success, cell = update_last_km_in_sheets(km)
                        if success:
                            updated_cell = f"תדלוקים 2026 ({cell})"
                    except Exception as e:
                        print(f"Sheet refuel update error: {e}")
                        
                self.send_response(200)
                self.send_header('Content-Type', 'application/json')
                self.send_cors_headers()
                self.end_headers()
                self.wfile.write(json.dumps({
                    'status': 'success',
                    'updated_destination': updated_cell,
                    'km': km_int,
                    'is_refuel': is_refuel_event
                }).encode('utf-8'))
                return
            except Exception as e:
                self.send_response(500)
                self.send_cors_headers()
                self.end_headers()
                self.wfile.write(json.dumps({'status': 'error', 'message': str(e)}).encode('utf-8'))
                return

        # 1.5 Save Car Settings endpoint: /api/car-settings
        if parsed.path == '/api/car-settings':
            try:
                data = json.loads(body)
                success, state = fuel_dashboard.save_vehicle_details(data)
                self.send_response(200 if success else 400)
                self.send_header('Content-Type', 'application/json')
                self.send_cors_headers()
                self.end_headers()
                self.wfile.write(json.dumps({'status': 'success' if success else 'error', 'settings': state}).encode('utf-8'))
                return
            except Exception as e:
                self.send_response(500)
                self.send_cors_headers()
                self.end_headers()
                self.wfile.write(json.dumps({'status': 'error', 'message': str(e)}).encode('utf-8'))
                return

        # 1.6 Switch or Add Vehicle: /api/vehicles
        if parsed.path == '/api/vehicles':
            try:
                data = json.loads(body)
                action = data.get('action', 'save')
                plate = str(data.get('plate', '')).strip()
                if action == 'switch' and plate:
                    state = fuel_dashboard.set_active_vehicle(plate)
                    self.send_response(200)
                    self.send_header('Content-Type', 'application/json')
                    self.send_cors_headers()
                    self.end_headers()
                    self.wfile.write(json.dumps({'status': 'success', 'active_plate': plate, 'state': state}).encode('utf-8'))
                    return
                else:
                    success, state = fuel_dashboard.save_vehicle_details(data)
                    self.send_response(200 if success else 400)
                    self.send_header('Content-Type', 'application/json')
                    self.send_cors_headers()
                    self.end_headers()
                    self.wfile.write(json.dumps({'status': 'success' if success else 'error', 'state': state}).encode('utf-8'))
                    return
            except Exception as e:
                self.send_response(500)
                self.send_cors_headers()
                self.end_headers()
                self.wfile.write(json.dumps({'status': 'error', 'message': str(e)}).encode('utf-8'))
                return

        # 1.7 Save Insurance Policy: /api/insurance
        if parsed.path == '/api/insurance':
            try:
                data = json.loads(body)
                success, record = fuel_dashboard.save_insurance_record(data)
                self.send_response(200 if success else 400)
                self.send_header('Content-Type', 'application/json')
                self.send_cors_headers()
                self.end_headers()
                self.wfile.write(json.dumps({'status': 'success' if success else 'error', 'insurance': record}).encode('utf-8'))
                return
            except Exception as e:
                self.send_response(500)
                self.send_cors_headers()
                self.end_headers()
                self.wfile.write(json.dumps({'status': 'error', 'message': str(e)}).encode('utf-8'))
                return

        # 1.8 Save Maintenance Record: /api/maintenance
        if parsed.path == '/api/maintenance':
            try:
                data = json.loads(body)
                success, record = fuel_dashboard.save_maintenance_record(data)
                self.send_response(200 if success else 400)
                self.send_header('Content-Type', 'application/json')
                self.send_cors_headers()
                self.end_headers()
                self.wfile.write(json.dumps({'status': 'success' if success else 'error', 'maintenance': record}).encode('utf-8'))
                return
            except Exception as e:
                self.send_response(500)
                self.send_cors_headers()
                self.end_headers()
                self.wfile.write(json.dumps({'status': 'error', 'message': str(e)}).encode('utf-8'))
                return

        # 2. Main SMS parsing endpoint
        text_to_parse = body
        km_from_payload = ''
        try:
            data = json.loads(body)
            text_to_parse = data.get('text') or data.get('message') or data.get('url') or body
            km_from_payload = data.get('km', '')
        except Exception:
            pass
            
        receipt, err = sonol_parser.parse_sonol_receipt(text_to_parse)
        if err or not receipt:
            self.send_response(400)
            self.send_cors_headers()
            self.end_headers()
            self.wfile.write(json.dumps({'status': 'error', 'message': err}).encode('utf-8'))
            return
            
        if km_from_payload:
            receipt['km'] = km_from_payload
            
        try:
            row_range = append_receipt_to_sheets(receipt)
            
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.send_cors_headers()
            self.end_headers()
            self.wfile.write(json.dumps({
                'status': 'success',
                'message': 'Receipt parsed and recorded',
                'row_range': row_range,
                'receipt': receipt
            }).encode('utf-8'))
        except Exception as ex:
            self.send_response(500)
            self.send_cors_headers()
            self.end_headers()
            self.wfile.write(json.dumps({'status': 'error', 'message': str(ex)}).encode('utf-8'))

def run_server(port=5088):
    server = ThreadingHTTPServer(('0.0.0.0', port), FuelWebhookHandler)
    print(f"Sonol Webhook Service 3.0 (with Dashboard & APK download) running on port {port}...")
    server.serve_forever()

if __name__ == '__main__':
    run_server()
