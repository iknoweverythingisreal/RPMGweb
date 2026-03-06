import psycopg2

try:
    conn = psycopg2.connect(
        dbname="postgres",
        user="postgres",
        password="bTbFieIrJcEKVnWv",
        host="db.vxycwelhariotzfwuffe.supabase.co",
        port="5432"
    )
    cur = conn.cursor()
    
    # Check event_items
    print(">>> Columns in event_items:")
    cur.execute("SELECT column_name, data_type FROM information_schema.columns WHERE table_name = 'event_items'")
    rows = cur.fetchall()
    for row in rows:
        print(f"  - {row[0]}: {row[1]}")
    
    # Check event_history
    print("\n>>> Columns in event_history:")
    cur.execute("SELECT column_name, data_type FROM information_schema.columns WHERE table_name = 'event_history'")
    rows = cur.fetchall()
    for row in rows:
        print(f"  - {row[0]}: {row[1]}")

    cur.close()
    conn.close()
except Exception as e:
    print(f"Error: {e}")
