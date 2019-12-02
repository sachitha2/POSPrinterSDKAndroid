package com.printersdk.demo.printerdemo;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.printer.command.CpclCommand;
import com.printer.command.EscCommand;
import com.printer.command.FactoryCommand;
import com.printer.command.LabelCommand;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static android.hardware.usb.UsbManager.ACTION_USB_DEVICE_ATTACHED;
import static android.hardware.usb.UsbManager.ACTION_USB_DEVICE_DETACHED;
import static com.printersdk.demo.printerdemo.Constant.ACTION_USB_PERMISSION;
import static com.printersdk.demo.printerdemo.Constant.MESSAGE_UPDATE_PARAMETER;
import static com.printersdk.demo.printerdemo.DeviceConnFactoryManager.ACTION_QUERY_PRINTER_STATE;
import static com.printersdk.demo.printerdemo.DeviceConnFactoryManager.CONN_STATE_FAILED;


public class MainActivity extends AppCompatActivity {
    private static final String	TAG	= "MainActivity";
    ArrayList<String>		per	= new ArrayList<>();
    private UsbManager		usbManager;
    private int			counts;
    private static final int	REQUEST_CODE = 0x004;



    private static final int CONN_STATE_DISCONN = 0x007;



    private static final int PRINTER_COMMAND_ERROR = 0x008;



    private byte[] esc = { 0x10, 0x04, 0x02 };



    private byte[] cpcl = { 0x1b, 0x68 };



    private byte[] tsc = { 0x1b, '!', '?' };

    private static final int	CONN_MOST_DEVICES	= 0x11;
    private static final int	CONN_PRINTER		= 0x12;
    private PendingIntent		mPermissionIntent;
    private				String[] permissions = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.BLUETOOTH
    };
    private String			usbName;
    private TextView		tvConnState;
    private ThreadPool		threadPool;



    private int		id = 0;
    private Spinner		mode_sp;
//    private byte[]		tscmode		= { 0x1f, 0x1b, 0x1f, (byte) 0xfc, 0x01, 0x02, 0x03, 0x33 };
//    private byte[]		cpclmode	= { 0x1f, 0x1b, 0x1f, (byte) 0xfc, 0x01, 0x02, 0x03, 0x44 };
//    private byte[]		escmode		= { 0x1f, 0x1b, 0x1f, (byte) 0xfc, 0x01, 0x02, 0x03, 0x55 };
//    private byte[]		selftest	= { 0x1f, 0x1b, 0x1f, (byte) 0x93, 0x10, 0x11, 0x12, 0x15, 0x16, 0x17, 0x10, 0x00 };
    private int		printcount	= 0;
    private boolean		continuityprint = false;
    /* private KeepConn keepConn; */
    @Override
    protected void onCreate( Bundle savedInstanceState )
    {
        super.onCreate( savedInstanceState );
        Log.e( TAG, "onCreate()" );
        setContentView( R.layout.activity_main );
        usbManager = (UsbManager) getSystemService( Context.USB_SERVICE );
        checkPermission();
        requestPermission();
        tvConnState	= (TextView) findViewById( R.id.tv_connState );
        initsp();
    }


    private void initsp()
    {
        List<String> list = new ArrayList<String>();
        list.add( getString( R.string.str_escmode ) );
        list.add( getString( R.string.str_tscmode ) );
        list.add( getString( R.string.str_cpclmode ) );
        ArrayAdapter<String> adapter = new ArrayAdapter<String>( this,
                android.R.layout.simple_spinner_item, list );
        adapter.setDropDownViewResource( android.R.layout.simple_list_item_single_choice );
        mode_sp = (Spinner) findViewById( R.id.mode_sp );
        mode_sp.setAdapter( adapter );
    }


    @Override
    protected void onStart()
    {
        super.onStart();
        IntentFilter filter = new IntentFilter( ACTION_USB_PERMISSION );
        filter.addAction( ACTION_USB_DEVICE_DETACHED );
        filter.addAction( ACTION_QUERY_PRINTER_STATE );
        filter.addAction( DeviceConnFactoryManager.ACTION_CONN_STATE );
        filter.addAction( ACTION_USB_DEVICE_ATTACHED );
        registerReceiver( receiver, filter );
    }


    private void checkPermission()
    {
        for ( String permission : permissions )
        {
            if ( PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission( this, permission ) )
            {
                per.add( permission );
            }
        }
    }


    private void requestPermission()
    {
        if ( per.size() > 0 )
        {
            String[] p = new String[per.size()];
            ActivityCompat.requestPermissions( this, per.toArray( p ), REQUEST_CODE );
        }
    }


    public void btnBluetoothConn( View view )
    {
        startActivityForResult( new Intent( this, BluetoothDeviceList.class ), Constant.BLUETOOTH_REQUEST_CODE );
    }

    public void btnReceiptPrint( View view )
    {
        if ( DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id] == null ||
                !DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].getConnState() )
        {
            Utils.toast( this, getString( R.string.str_cann_printer ) );
            return;
        }
        threadPool = ThreadPool.getInstantiation();
        threadPool.addTask( new Runnable()
        {
            @Override
            public void run()
            {
                if ( DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].getCurrentPrinterCommand() == PrinterCommand.ESC )
                {
                    sendReceiptWithResponse();
                } else {
                    mHandler.obtainMessage( PRINTER_COMMAND_ERROR ).sendToTarget();
                }
            }
        } );
    }



    private void sendCpcl( int id )
    {
        CpclCommand cpcl = new CpclCommand();
        cpcl.addInitializePrinter( 1130, 1 );
        cpcl.addJustification( CpclCommand.ALIGNMENT.CENTER );
        cpcl.addSetmag( 1, 1 );
        cpcl.addText( CpclCommand.TEXT_FONT.FONT_4, 0, 30, "SampSAchitha Hirushanle" );
        cpcl.addSetmag( 0, 0 );
        cpcl.addJustification( CpclCommand.ALIGNMENT.LEFT );
        cpcl.addText( CpclCommand.TEXT_FONT.FONT_4, 0, 65, "Print text" );
        cpcl.addText( CpclCommand.TEXT_FONT.FONT_4, 0, 95, "Welcom to use our printer!" );
        cpcl.addText( CpclCommand.TEXT_FONT.FONT_13, 0, 135, "Sachitha Hirushann" );
        cpcl.addText( CpclCommand.TEXT_FONT.FONT_4, 0, 195, "Hellooo" );
        cpcl.addJustification( CpclCommand.ALIGNMENT.CENTER );
        cpcl.addText( CpclCommand.TEXT_FONT.FONT_4, 0, 195, "Hi mam" );
        cpcl.addJustification( CpclCommand.ALIGNMENT.RIGHT );
        cpcl.addText( CpclCommand.TEXT_FONT.FONT_4, 0, 195, "588" );
        cpcl.addJustification( CpclCommand.ALIGNMENT.LEFT );
        cpcl.addText( CpclCommand.TEXT_FONT.FONT_4, 0, 230, "Print bitmap!" );
        Bitmap bitmap = BitmapFactory.decodeResource( getResources(), R.drawable.printer);
        cpcl.addEGraphics( 0, 255, 385, bitmap );
        cpcl.addText( CpclCommand.TEXT_FONT.FONT_4, 0, 645, "Print code128!" );
        cpcl.addBarcodeText( 5, 2 );
        cpcl.addBarcode( CpclCommand.COMMAND.BARCODE, CpclCommand.CPCLBARCODETYPE.CODE128, 50, 0, 680, "SMARNET" );
        cpcl.addText( CpclCommand.TEXT_FONT.FONT_4, 0, 775, "Print QRcode" );
        cpcl.addBQrcode( 0, 810, "QRcode" );
        cpcl.addJustification( CpclCommand.ALIGNMENT.CENTER );
        cpcl.addText( CpclCommand.TEXT_FONT.FONT_4, 0, 1010, "Completed" );
        cpcl.addJustification( CpclCommand.ALIGNMENT.LEFT );
        cpcl.addPrint();
        Vector<Byte> datas = cpcl.getCommand();

        DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].sendDataImmediately( datas );
    }

    public void btnDisConn( View view )
    {
        if ( DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id] == null ||
                !DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].getConnState() )
        {
            Utils.toast( this, getString( R.string.str_cann_printer ) );
            return;
        }
        mHandler.obtainMessage( CONN_STATE_DISCONN ).sendToTarget();
    }





    public void btnPrintXml( View view )
    {
        View		v		= View.inflate( this, R.layout.pj, null );
        TableLayout	tableLayout	= (TableLayout) v.findViewById( R.id.li );
        TextView	jine		= (TextView) v.findViewById( R.id.jine );
        TextView	pep		= (TextView) v.findViewById( R.id.pep );
        tableLayout.addView( ctv( MainActivity.this, "A\nB\nC", 8, 3 ) );
        tableLayout.addView( ctv( MainActivity.this, "A1", 109, 899 ) );
        tableLayout.addView( ctv( MainActivity.this, "A2", 15, 4 ) );
        tableLayout.addView( ctv( MainActivity.this, "A3", 8, 3 ) );
        tableLayout.addView( ctv( MainActivity.this, "A4", 10, 8 ) );
        tableLayout.addView( ctv( MainActivity.this, "A5", 15, 4 ) );
        tableLayout.addView( ctv( MainActivity.this, "A6", 8, 3 ) );
        tableLayout.addView( ctv( MainActivity.this, "A7", 10, 8 ) );
        tableLayout.addView( ctv( MainActivity.this, "A8", 15, 4 ) );
        tableLayout.addView( ctv( MainActivity.this, "A9", 8, 3 ) );
        tableLayout.addView( ctv( MainActivity.this, "A10", 10, 8 ) );
        tableLayout.addView( ctv( MainActivity.this, "A11", 15, 4 ) );
        tableLayout.addView( ctv( MainActivity.this, "A12", 8, 3 ) );
        tableLayout.addView( ctv( MainActivity.this, "A13", 10, 8 ) );
        tableLayout.addView( ctv( MainActivity.this, "A14", 15, 4 ) );
        jine.setText( "998" );
        pep.setText( "Sam" );
        final Bitmap bitmap = convertViewToBitmap( v );
        threadPool = ThreadPool.getInstantiation();
        threadPool.addTask( new Runnable()
        {
            @Override
            public void run()
            {
                if ( DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id] == null ||
                        !DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].getConnState() )
                {
                    mHandler.obtainMessage( CONN_PRINTER ).sendToTarget();
                    return;
                }

                if ( DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].getCurrentPrinterCommand() == PrinterCommand.CPCL )
                {
                    CpclCommand cpcl = new CpclCommand();
                    cpcl.addInitializePrinter( 1500, 1 );
                    cpcl.addCGraphics( 0, 0, (80 - 10) * 8, bitmap );
                    cpcl.addPrint();
                    DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].sendDataImmediately( cpcl.getCommand() );
                } else if ( DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].getCurrentPrinterCommand() == PrinterCommand.TSC )
                {
                    LabelCommand labelCommand = new LabelCommand();
                    labelCommand.addSize( 80, 180 );
                    labelCommand.addCls();
                    labelCommand.addBitmap( 0, 0, (80 - 10) * 8, bitmap );
                    labelCommand.addPrint( 1 );
                    DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].sendDataImmediately( labelCommand.getCommand() );
                }else if ( DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].getCurrentPrinterCommand() == PrinterCommand.ESC )
                {
                    EscCommand esc = new EscCommand();
                    esc.addInitializePrinter();
                    esc.addInitializePrinter();
                    esc.addRastBitImage( bitmap, (80 - 10) * 8, 0 );
                    esc.addPrintAndLineFeed();
                    esc.addPrintAndLineFeed();
                    esc.addPrintAndLineFeed();
                    DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].sendDataImmediately( esc.getCommand() );
                }
            }
        } );
    }


    public static Bitmap convertViewToBitmap( View view )
    {
        view.measure( View.MeasureSpec.makeMeasureSpec( 0, View.MeasureSpec.UNSPECIFIED ), View.MeasureSpec.makeMeasureSpec( 0, View.MeasureSpec.UNSPECIFIED ) );
        view.layout( 0, 0, view.getMeasuredWidth(), view.getMeasuredHeight() );
        view.buildDrawingCache();
        Bitmap bitmap = view.getDrawingCache();
        return(bitmap);
    }


    private TableRow ctv( Context context, String name, int k, int n )
    {
        TableRow tb = new TableRow( context );
        tb.setLayoutParams( new TableLayout.LayoutParams( TableLayout.LayoutParams.WRAP_CONTENT, TableLayout.LayoutParams.WRAP_CONTENT ) );
        TextView tv1 = new TextView( context );
        tv1.setLayoutParams( new TableRow.LayoutParams( TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT ) );
        tv1.setText( name );
        tv1.setTextColor( Color.BLACK );
        tv1.setTextSize( 30 );
        tb.addView( tv1 );
        TextView tv2 = new TextView( context );
        tv2.setLayoutParams( new TableRow.LayoutParams( TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT ) );
        tv2.setText( k + "" );
        tv2.setTextColor( Color.BLACK );
        tv2.setTextSize( 30 );
        tb.addView( tv2 );
        TextView tv3 = new TextView( context );
        tv3.setLayoutParams( new TableRow.LayoutParams( TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT ) );
        tv3.setText( n + "" );
        tv3.setTextColor( Color.BLACK );
        tv3.setTextSize( 30 );
        tb.addView( tv3 );
        return(tb);
    }


    public void btnPrinterState( View view )
    {

        if ( DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id] == null ||
                !DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].getConnState() )
        {
            Utils.toast( this, getString( R.string.str_cann_printer ) );
            return;
        }
        DeviceConnFactoryManager.whichFlag = true;
        ThreadPool.getInstantiation().addTask( new Runnable()
        {
            @Override
            public void run()
            {
                Vector<Byte> data = new Vector<>( esc.length );
                if ( DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].getCurrentPrinterCommand() == PrinterCommand.ESC )
                {
                    for ( int i = 0; i < esc.length; i++ )
                    {
                        data.add( esc[i] );
                    }
                    DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].sendDataImmediately( data );
                }else if ( DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].getCurrentPrinterCommand() == PrinterCommand.TSC )
                {
                    for ( int i = 0; i < tsc.length; i++ )
                    {
                        data.add( tsc[i] );
                    }
                    DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].sendDataImmediately( data );
                }else if ( DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].getCurrentPrinterCommand() == PrinterCommand.CPCL )
                {
                    for ( int i = 0; i < cpcl.length; i++ )
                    {
                        data.add( cpcl[i] );
                    }
                    DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].sendDataImmediately( data );
                }
            }
        } );
    }



    public void btnPrinterPower(View v){

        if ( DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id] == null ||
                !DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].getConnState() )
        {
            Utils.toast( this, getString( R.string.str_cann_printer ) );
            return;
        }
        DeviceConnFactoryManager.whichFlag = false;
        ThreadPool.getInstantiation().addTask( new Runnable()
        {
            @Override
            public void run()
            {
                if ( DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].getCurrentPrinterCommand() == PrinterCommand.ESC ) {
                    byte[] bytes = FactoryCommand.searchPower(0);
                    Vector<Byte> data = new Vector<>(bytes.length);
                    for (int i = 0; i < bytes.length; i++) {
                        data.add(bytes[i]);
                    }
                    DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].sendDataImmediately( data );
                }
            }
        } );
    }







    public void btnModeChange( View view )
    {
        if ( DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id] == null ||
                !DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].getConnState() )
        {
            Utils.toast( this, getString( R.string.str_cann_printer ) );
            return;
        }
        if ( DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].getCurrentPrinterCommand() == PrinterCommand.CPCL )
        {
            CpclCommand cpclCommand = new CpclCommand();
            cpclCommand.addInitializePrinter();
            DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].sendDataImmediately( cpclCommand.getCommand() );
        }
        int sp_no = mode_sp.getSelectedItemPosition(); //0票据,1标签,2面单
        byte[] bytes = FactoryCommand.changeWorkMode(sp_no);
        Vector<Byte> data = new Vector<>();
        for (int i = 0; i < bytes.length; i++) {
            data.add(bytes[i]);
        }
        DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].sendDataImmediately( data );
        DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].closePort( id );
    }


    @Override
    protected void onActivityResult( int requestCode, int resultCode, Intent data )
    {
        super.onActivityResult( requestCode, resultCode, data );
        if ( resultCode == RESULT_OK )
        {
            switch ( requestCode )
            {

                case Constant.BLUETOOTH_REQUEST_CODE: {
                    closeport();

                    String macAddress = data.getStringExtra( BluetoothDeviceList.EXTRA_DEVICE_ADDRESS );

                    new DeviceConnFactoryManager.Build()
                            .setId( id )

                            .setConnMethod( DeviceConnFactoryManager.CONN_METHOD.BLUETOOTH )

                            .setMacAddress( macAddress )
                            .build();

                    Log.d(TAG, "onActivityResult: 58"+id);
                    threadPool = ThreadPool.getInstantiation();
                    threadPool.addTask( new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].openPort();
                        }
                    } );

                    break;
                }

                case Constant.USB_REQUEST_CODE: {
                    closeport();

                    usbName = data.getStringExtra( UsbDeviceList.USB_NAME );

                    UsbDevice usbDevice = Utils.getUsbDeviceFromName( MainActivity.this, usbName );

                    if ( usbManager.hasPermission( usbDevice ) )
                    {
                        usbConn( usbDevice );
                    } else {
                        mPermissionIntent = PendingIntent.getBroadcast( this, 0, new Intent( ACTION_USB_PERMISSION ), 0 );
                        usbManager.requestPermission( usbDevice, mPermissionIntent );
                    }
                    break;
                }

                case Constant.SERIALPORT_REQUEST_CODE:
                    closeport();

                    int baudrate = data.getIntExtra( Constant.SERIALPORTBAUDRATE, 0 );

                    String path = data.getStringExtra( Constant.SERIALPORTPATH );

                    if ( baudrate != 0 && !TextUtils.isEmpty( path ) )
                    {

                        new DeviceConnFactoryManager.Build()

                                .setConnMethod( DeviceConnFactoryManager.CONN_METHOD.SERIAL_PORT )
                                .setId( id )

                                .setBaudrate( baudrate )

                                .setSerialPort( path )
                                .build();

                        DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].openPort();
                    }
                    break;
                case CONN_MOST_DEVICES:
                    id = data.getIntExtra( "id", -1 );
                    if ( DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id] != null &&
                            DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].getConnState() )
                    {
                        tvConnState.setText( getString( R.string.str_conn_state_connected ) + "\n" + getConnDeviceInfo() );
                    } else {
                        tvConnState.setText( getString( R.string.str_conn_state_disconnect ) );
                    }
                    break;
                default:
                    break;
            }
        }
    }


    private void closeport()
    {
        if ( DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id] != null &&DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].mPort != null )
        {
            DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].reader.cancel();
            DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].mPort.closePort();
            DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].mPort = null;
        }
    }


    private void usbConn( UsbDevice usbDevice )
    {
        new DeviceConnFactoryManager.Build()
                .setId( id )
                .setConnMethod( DeviceConnFactoryManager.CONN_METHOD.USB )
                .setUsbDevice( usbDevice )
                .setContext( this )
                .build();
        DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].openPort();
    }


    void sendLabel()
    {
        LabelCommand tsc = new LabelCommand();

        tsc.addTear( EscCommand.ENABLE.ON );

        tsc.addSize( 80, 90 );

        tsc.addGap( 0 );

        tsc.addDirection( LabelCommand.DIRECTION.FORWARD, LabelCommand.MIRROR.NORMAL );

        tsc.addQueryPrinterStatus( LabelCommand.RESPONSE_MODE.ON );

        tsc.addReference( 0, 0 );

        tsc.addCls();

        tsc.addText( 10, 0, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1,
                "Welcome to use our printer" );

        Bitmap b = BitmapFactory.decodeResource( getResources(), R.drawable.printer);
        tsc.addBitmap( 10, 20, LabelCommand.BITMAP_MODE.OVERWRITE, 300, b );

        tsc.addQRCode( 10, 330, LabelCommand.EEC.LEVEL_L, 5, LabelCommand.ROTATION.ROTATION_0, "Printer" );

        tsc.add1DBarcode( 10, 450, LabelCommand.BARCODETYPE.CODE128, 100, LabelCommand.READABEL.EANBEL, LabelCommand.ROTATION.ROTATION_0, "SMARNET" );

        tsc.addText(10, 580, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1,
                "简体字" );

        tsc.addText(100, 580, LabelCommand.FONTTYPE.TRADITIONAL_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1,
                "繁體字" );

        tsc.addText(190, 580, LabelCommand.FONTTYPE.KOREAN, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1,
                "한국어" );


        tsc.addPrint( 1, 1 );


        tsc.addSound( 2, 100 );
        tsc.addCashdrwer( LabelCommand.FOOT.F5, 255, 255 );
        Vector<Byte> datas = tsc.getCommand();

        if ( DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id] == null )
        {
            Log.d(TAG, "sendLabel: 打印机为空");
            return;
        }
        DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].sendDataImmediately( datas );
    }




    void sendLabel( int id )
    {
        LabelCommand tsc = new LabelCommand();

        tsc.addTear( EscCommand.ENABLE.ON );

        tsc.addSize( 60, 60 );

        tsc.addGap( 0 );

        tsc.addDirection( LabelCommand.DIRECTION.BACKWARD, LabelCommand.MIRROR.NORMAL );

        tsc.addQueryPrinterStatus( LabelCommand.RESPONSE_MODE.ON );

        tsc.addReference( 0, 0 );

        tsc.addCls();

        tsc.addText( 10, 0, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1,
                "Welcome to use our printer!" );

        Bitmap b = BitmapFactory.decodeResource( getResources(), R.drawable.printer);
        tsc.addBitmap( 10, 20, LabelCommand.BITMAP_MODE.OVERWRITE, 300, b );

        tsc.addQRCode( 250, 80, LabelCommand.EEC.LEVEL_L, 5, LabelCommand.ROTATION.ROTATION_0, " Printer" );

        tsc.add1DBarcode( 20, 250, LabelCommand.BARCODETYPE.CODE128, 100, LabelCommand.READABEL.EANBEL, LabelCommand.ROTATION.ROTATION_0, "SMARNET" );

        tsc.addPrint( 1, 1 );


        tsc.addSound( 2, 100 );
        tsc.addCashdrwer( LabelCommand.FOOT.F5, 255, 255 );
        Vector<Byte> datas = tsc.getCommand();

        if ( DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id] == null )
        {
            return;
        }
        DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].sendDataImmediately( datas );
    }



    void sendReceiptWithResponse()
    {
        EscCommand esc = new EscCommand();
        esc.addInitializePrinter();
        esc.addPrintAndFeedLines( (byte) 3 );

        esc.addSelectJustification( EscCommand.JUSTIFICATION.CENTER );

        esc.addSelectPrintModes( EscCommand.FONT.FONTA, EscCommand.ENABLE.OFF, EscCommand.ENABLE.ON, EscCommand.ENABLE.ON, EscCommand.ENABLE.OFF );

        esc.addText( "TRANS LANKA\n" );
        esc.addPrintAndLineFeed();


        esc.addSelectPrintModes( EscCommand.FONT.FONTA, EscCommand.ENABLE.OFF, EscCommand.ENABLE.OFF, EscCommand.ENABLE.OFF, EscCommand.ENABLE.OFF );

        esc.addSelectJustification( EscCommand.JUSTIFICATION.LEFT );

        esc.addText( "Print text\n" );

        esc.addText( "Welcome to use our printer!\n" );


        String message = "hellooo\n";
        esc.addText( message, "GB2312" );
        esc.addPrintAndLineFeed();


        esc.addText( "A" );
        esc.addSetHorAndVerMotionUnits( (byte) 7, (byte) 0 );
        esc.addSetAbsolutePrintPosition( (short) 6 );
        esc.addText( "AA" );
        esc.addSetAbsolutePrintPosition( (short) 10 );
        esc.addText( "BB" );
        esc.addPrintAndLineFeed();


        esc.addText( "Print bitmap!\n" );
        Bitmap b = BitmapFactory.decodeResource( getResources(),
                R.drawable.printer);

        esc.addRastBitImage( b, 380, 0 );


        esc.addText( "Print code128\n" );
        esc.addSelectPrintingPositionForHRICharacters( EscCommand.HRI_POSITION.BELOW );

        esc.addSetBarcodeHeight( (byte) 60 );


        esc.addSetBarcodeWidth( (byte) 1 );


        esc.addCODE128( esc.genCodeB( "SMARNET" ) );
        esc.addPrintAndLineFeed();



        esc.addText( "Print QRcode\n" );



        esc.addSelectErrorCorrectionLevelForQRCode( (byte) 0x31 );


        esc.addSelectSizeOfModuleForQRCode( (byte) 3 );

        esc.addStoreQRCodeData( "Printer" );
        esc.addPrintQRCode();
        esc.addPrintAndLineFeed();


        esc.addSelectJustification( EscCommand.JUSTIFICATION.CENTER );



        esc.addText( "Completed!\r\n" );


        esc.addGeneratePlus( LabelCommand.FOOT.F5, (byte) 255, (byte) 255 );
        esc.addPrintAndFeedLines( (byte) 8 );

        byte[] bytes = { 29, 114, 1 };
        esc.addUserCommand( bytes );
        Vector<Byte> datas = esc.getCommand();

        DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].sendDataImmediately( datas );
    }


    void sendReceiptWithResponse( int id )
    {
        EscCommand esc = new EscCommand();
        esc.addInitializePrinter();
        esc.addPrintAndFeedLines( (byte) 3 );

        esc.addSelectJustification( EscCommand.JUSTIFICATION.CENTER );

        esc.addSelectPrintModes( EscCommand.FONT.FONTA, EscCommand.ENABLE.OFF, EscCommand.ENABLE.ON, EscCommand.ENABLE.ON, EscCommand.ENABLE.OFF );

        esc.addText( "BB\n" );
        esc.addPrintAndLineFeed();


        esc.addSelectPrintModes( EscCommand.FONT.FONTA, EscCommand.ENABLE.OFF, EscCommand.ENABLE.OFF, EscCommand.ENABLE.OFF, EscCommand.ENABLE.OFF );

        esc.addSelectJustification( EscCommand.JUSTIFICATION.LEFT );

        esc.addText( "Print text\n" );

        esc.addText( "Welcome to use the printer!\n" );


        String message = "票據打印機繁体\n";
        esc.addText( message, "GB2312" );
        esc.addPrintAndLineFeed();


        esc.addText( "打印" );
        esc.addSetHorAndVerMotionUnits( (byte) 7, (byte) 0 );
        esc.addSetAbsolutePrintPosition( (short) 6 );
        esc.addText( "网络" );
        esc.addSetAbsolutePrintPosition( (short) 10 );
        esc.addText( "设备" );
        esc.addPrintAndLineFeed();


        esc.addText( "Print bitmap!\n" );
        Bitmap b = BitmapFactory.decodeResource( getResources(),
                R.drawable.printer);

        esc.addRastBitImage( b, 380, 0 );


        esc.addText( "Print code128\n" );
        esc.addSelectPrintingPositionForHRICharacters( EscCommand.HRI_POSITION.BELOW );

        esc.addSetBarcodeHeight( (byte) 60 );

        esc.addSetBarcodeWidth( (byte) 1 );

        esc.addCODE128( esc.genCodeB( "SMARNET" ) );
        esc.addPrintAndLineFeed();



        esc.addText( "Print QRcode\n" );

        esc.addSelectErrorCorrectionLevelForQRCode( (byte) 0x31 );

        esc.addSelectSizeOfModuleForQRCode( (byte) 3 );

        esc.addStoreQRCodeData( "Printer" );
        esc.addPrintQRCode(); /* 打印QRCode */
        esc.addPrintAndLineFeed();


        esc.addSelectJustification( EscCommand.JUSTIFICATION.CENTER );

        esc.addText( "Completed!\r\n" );


        esc.addGeneratePlus( LabelCommand.FOOT.F5, (byte) 255, (byte) 255 );
        esc.addPrintAndFeedLines( (byte) 8 );

        byte[] bytes = { 29, 114, 1 };
        Vector<Byte> datas = esc.getCommand();

        DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].sendDataImmediately( datas );
    }

    private BroadcastReceiver receiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive( Context context, Intent intent )
        {
            String action = intent.getAction();
            switch ( action )
            {
                case ACTION_USB_PERMISSION:
                    synchronized (this) {
                        UsbDevice device = intent.getParcelableExtra( UsbManager.EXTRA_DEVICE );
                        if ( intent.getBooleanExtra( UsbManager.EXTRA_PERMISSION_GRANTED, false ) )
                        {
                            if ( device != null )
                            {
                                System.out.println( "permission ok for device " + device );
                                usbConn( device );
                            }
                        } else {
                            System.out.println( "permission denied for device " + device );
                        }
                    }
                    break;

                case ACTION_USB_DEVICE_DETACHED:
                    mHandler.obtainMessage( CONN_STATE_DISCONN ).sendToTarget();
                    break;
                case DeviceConnFactoryManager.ACTION_CONN_STATE:
                    int state = intent.getIntExtra( DeviceConnFactoryManager.STATE, -1 );
                    int deviceId = intent.getIntExtra( DeviceConnFactoryManager.DEVICE_ID, -1 );
                    switch ( state )
                    {
                        case DeviceConnFactoryManager.CONN_STATE_DISCONNECT:
                            if ( id == deviceId )
                            {
                                tvConnState.setText( getString( R.string.str_conn_state_disconnect ) );
                            }
                            break;
                        case DeviceConnFactoryManager.CONN_STATE_CONNECTING:
                            tvConnState.setText( getString( R.string.str_conn_state_connecting ) );
                            break;
                        case DeviceConnFactoryManager.CONN_STATE_CONNECTED:
                            tvConnState.setText( getString( R.string.str_conn_state_connected ) + "\n" + getConnDeviceInfo() );

                            break;
                        case CONN_STATE_FAILED:
                            Utils.toast( MainActivity.this, getString( R.string.str_conn_fail ) );
                            /* wificonn=false; */
                            tvConnState.setText( getString( R.string.str_conn_state_disconnect ) );
                            break;
                        default:
                            break;
                    }
                    break;
                case ACTION_QUERY_PRINTER_STATE:
                    if ( counts >= 0 )
                    {
                        if ( continuityprint )
                        {
                            printcount++;
                            Utils.toast( MainActivity.this, getString( R.string.str_continuityprinter ) + " " + printcount );
                        }
                        if ( counts != 0 )
                        {

                        }else {
                            continuityprint = false;
                        }
                    }
                    break;
                default:
                    break;
            }
        }
    };
    private Handler mHandler = new Handler()
    {
        @Override
        public void handleMessage( Message msg )
        {
            switch ( msg.what )
            {
                case CONN_STATE_DISCONN:
                    if ( DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id] != null || !DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].getConnState() )
                    {
                        DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].closePort( id );
                        Utils.toast( MainActivity.this, getString( R.string.str_disconnect_success ) );
                    }
                    break;
                case PRINTER_COMMAND_ERROR:
                    Utils.toast( MainActivity.this, getString( R.string.str_choice_printer_command ) );
                    break;
                case CONN_PRINTER:
                    Utils.toast( MainActivity.this, getString( R.string.str_cann_printer ) );
                    break;
                case MESSAGE_UPDATE_PARAMETER:
                    String strIp = msg.getData().getString( "Ip" );
                    String strPort = msg.getData().getString( "Port" );

                    new DeviceConnFactoryManager.Build()

                            .setConnMethod( DeviceConnFactoryManager.CONN_METHOD.WIFI )

                            .setIp( strIp )

                            .setId( id )

                            .setPort( Integer.parseInt( strPort ) )
                            .build();
                    threadPool = ThreadPool.getInstantiation();
                    threadPool.addTask( new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].openPort();
                        }
                    } );
                    break;
                default:
                    new DeviceConnFactoryManager.Build()

                            .setConnMethod( DeviceConnFactoryManager.CONN_METHOD.WIFI )

                            .setIp( "192.168.2.227" )

                            .setId( id )

                            .setPort( 9100 )
                            .build();
                    threadPool.addTask( new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].openPort();
                        }
                    } );
                    break;
            }
        }
    };

    @Override
    protected void onStop()
    {
        super.onStop();
        unregisterReceiver( receiver );
    }


    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        Log.e( TAG, "onDestroy()" );
        DeviceConnFactoryManager.closeAllPort();
        if ( threadPool != null )
        {
            threadPool.stopThreadPool();
            threadPool = null;
        }
    }


    private String getConnDeviceInfo()
    {
        String				str				= "";
        DeviceConnFactoryManager	deviceConnFactoryManager	= DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id];
        if ( deviceConnFactoryManager != null
                && deviceConnFactoryManager.getConnState() )
        {
            if ( "USB".equals( deviceConnFactoryManager.getConnMethod().toString() ) )
            {
                str	+= "USB\n";
                str	+= "USB Name: " + deviceConnFactoryManager.usbDevice().getDeviceName();
            } else if ( "WIFI".equals( deviceConnFactoryManager.getConnMethod().toString() ) )
            {
                str	+= "WIFI\n";
                str	+= "IP: " + deviceConnFactoryManager.getIp() + "\t";
                str	+= "Port: " + deviceConnFactoryManager.getPort();
            } else if ( "BLUETOOTH".equals( deviceConnFactoryManager.getConnMethod().toString() ) )
            {
                str	+= "BLUETOOTH\n";
                str	+= "MacAddress: " + deviceConnFactoryManager.getMacAddress();
            } else if ( "SERIAL_PORT".equals( deviceConnFactoryManager.getConnMethod().toString() ) )
            {
                str	+= "SERIAL_PORT\n";
                str	+= "Path: " + deviceConnFactoryManager.getSerialPortPath() + "\t";
                str	+= "Baudrate: " + deviceConnFactoryManager.getBaudrate();
            }
        }
        return(str);
    }
}