using NdefLibrary.Ndef;
using System;
using System.IO;
using System.Linq;
using System.Collections.Generic;
using System.Runtime.InteropServices.WindowsRuntime;
using Windows.Foundation;
using Windows.Foundation.Collections;
using Windows.UI.Core;
using Windows.UI.Popups;
using Windows.UI.Xaml;
using Windows.UI.Xaml.Controls;
using Windows.UI.Xaml.Controls.Primitives;
using Windows.UI.Xaml.Data;
using Windows.UI.Xaml.Input;
using Windows.UI.Xaml.Media;
using Windows.UI.Xaml.Navigation;
using Windows.Storage;
using Windows.Storage.Streams;
using Windows.Storage.Pickers;
using Windows.ApplicationModel.Core;
using Windows.ApplicationModel.Activation;
using Windows.Networking.Proximity;
using Windows.Networking.Sockets;
using Windows.Devices.Bluetooth.Rfcomm;
using Windows.Security.ExchangeActiveSyncProvisioning;

namespace NfcBluetoothApp
{
    public sealed partial class Send : Page
    {
        private static readonly Guid serviceUuid =
            Guid.Parse("00001101-0000-1000-8000-00805f9b34fb");
        private const UInt16 SdpServiceNameAttributeId = 0x100;
        private const byte SdpServiceNameAttributeType = (4 << 3) | 5;
        private const string SdpServiceName = "bluetoothServ";

        private EasClientDeviceInformation deviceInformation;
        private RfcommServiceProvider rfcommProvider;
        private StreamSocketListener socketListener;
        private StreamSocket socket;
        private DataReader reader;
        private DataWriter writer;
        private IInputStream stream;
        private Windows.Storage.Streams.Buffer buffer;
        private const uint bufferSize = 16384;
        private StorageFile file;
        private String deviceName;
        private ulong fileSize;
        private bool started = false;
        private long messageId = -1;

        public Send()
        {        
            this.InitializeComponent();
            this.NavigationCacheMode = NavigationCacheMode.Disabled;
            Windows.Phone.UI.Input.HardwareButtons.BackPressed += hardwareBackPressed;
            deviceInformation = new EasClientDeviceInformation();
            deviceName = deviceInformation.FriendlyName;
        }

        //-------------------------------------------------------------------------przycisk wybierz plik
        private void selectFile(object sender, RoutedEventArgs e)
        {
            CoreApplicationView view = CoreApplication.GetCurrentView();
            view.Activated += viewActivated; 
            view = CoreApplication.GetCurrentView();
            FileOpenPicker filePicker = new FileOpenPicker();
            filePicker.SuggestedStartLocation = PickerLocationId.ComputerFolder;
            filePicker.ViewMode = PickerViewMode.Thumbnail;
            filePicker.FileTypeFilter.Add("*");
            filePicker.PickSingleFileAndContinue();
        }

        //-------------------------------------------------------------------------kontunuuj po wybraniu pliku
        private void viewActivated(CoreApplicationView sender, IActivatedEventArgs args1)
        {
            FileOpenPickerContinuationEventArgs args =
                args1 as FileOpenPickerContinuationEventArgs;

            if (args != null)
            {
                if (args.Files.Count == 0) return;

                file = args.Files[0];
                selectedFile();
            }
        }

        async private void selectedFile()
        {

            var basicProperties = await file.GetBasicPropertiesAsync();
            fileSize = basicProperties.Size;
            statusText.Text = "Nazwa pliku: " + file.Name + "\nRozmiar: " + fileSize + " bajtów";
            sendButton.Visibility = Visibility.Visible;
        }

        //-------------------------------------------------------------------------przycisk wyslij
        private void sendFile(object sender, RoutedEventArgs e)
        {
            startServer();
            fileButton.Visibility = Visibility.Collapsed;
            sendButton.Visibility = Visibility.Collapsed;
            statusText.Text = "";
            tapImage.Visibility = Visibility.Visible;
            tapText.Visibility = Visibility.Visible;
            if (messageId != -1)
                App.proximityDevice.StopPublishingMessage(messageId);
            messageId = App.proximityDevice.PublishBinaryMessage(
                "NDEF", mNdefMessage().ToByteArray().AsBuffer(), MessagePublished);
        }

        //-------------------------------------------------------------------------wiadomosc NDEF wyslana
        async private void MessagePublished(ProximityDevice sender, long messageId)
        {
            await this.Dispatcher.RunAsync(CoreDispatcherPriority.Normal, () =>
            {
                started = true;
                tapImage.Visibility = Visibility.Collapsed;
                tapText.Visibility = Visibility.Collapsed;
                statusText.Text = "Oczekiwanie na połączenie";
                waiting.IsActive = true;
                App.proximityDevice.StopPublishingMessage(messageId);
                messageId = -1;
            });
        }

        //-------------------------------------------------------------------------wiadomosc NDEF
        private NdefMessage mNdefMessage()
        {
            var validateRecord = new NdefTextRecord { Text = "ngi8aw6", LanguageCode = "en" };
            var deviceNameRecord = new NdefTextRecord { Text = deviceName, LanguageCode = "en" };
            var fileNameRecord = new NdefTextRecord { Text = file.Name, LanguageCode = "en" };
            var fileSizeRecord = new NdefTextRecord { Text = fileSize.ToString(), LanguageCode = "en" };
            var androidAppRecord = new NdefAndroidAppRecord { PackageName = "com.nfcbluetoothapp.nfcbluetoothapp" };

            return new NdefMessage { validateRecord, deviceNameRecord, fileNameRecord, 
                fileSizeRecord, androidAppRecord };
        }

        //-------------------------------------------------------------------------uruchamianie serwera
        async private void startServer()
        {
            try
            {
                rfcommProvider = await RfcommServiceProvider.CreateAsync(
                    RfcommServiceId.FromUuid(serviceUuid));
            }
            catch (Exception e)
            {
                if ((uint)e.HResult == 0x9000000F)
                {
                    disconnect();
                    System.Diagnostics.Debug.WriteLine(e.StackTrace);
                    showPopupDialog("Bluetooth jest wyłączone!\nPowrót");
                    return;
                }
            }

            socketListener = new StreamSocketListener();
            socketListener.ConnectionReceived += OnConnectionReceived;
            await socketListener.BindServiceNameAsync(rfcommProvider.ServiceId.AsString());
            var sdpWriter = new DataWriter();
            sdpWriter.WriteByte(SdpServiceNameAttributeType);
            sdpWriter.WriteByte((byte)SdpServiceName.Length);
            sdpWriter.UnicodeEncoding = Windows.Storage.Streams.UnicodeEncoding.Utf8;
            sdpWriter.WriteString(SdpServiceName);
            rfcommProvider.SdpRawAttributes.Add(SdpServiceNameAttributeId, sdpWriter.DetachBuffer());
            rfcommProvider.StartAdvertising(socketListener);
        }

        //-------------------------------------------------------------------------nowe polaczenie
        async private void OnConnectionReceived(StreamSocketListener sender, 
            StreamSocketListenerConnectionReceivedEventArgs args)
        {
            socketListener.Dispose();
            socketListener = null;

        //-------------------------------------------------------------------------tworzenie socketu
            socket = args.Socket;

            await this.Dispatcher.RunAsync(CoreDispatcherPriority.Normal, () =>
            {
                waiting.IsActive = false;
                progress.Visibility = Visibility.Visible;
                statusText.Text = "Wysyłanie pliku";
            });

            sendFile();
        }

        async private void sendFile()
        {
            //---------------------------------------------------------------------strumienie danych
            try
            {
                reader = new DataReader(socket.InputStream);
                writer = new DataWriter(socket.OutputStream);
            }
            catch (Exception e)
            {
                disconnect();
                System.Diagnostics.Debug.WriteLine(e.StackTrace);
                showPopupDialog("Wystąpił błąd połączenia!\nPowrót");
                return;
            }

            //---------------------------------------------------------------------strumien pliku
            try
            {
                stream = await file.OpenAsync(FileAccessMode.Read);
            }
            catch (Exception e)
            {
                disconnect();
                System.Diagnostics.Debug.WriteLine(e.StackTrace);
                showPopupDialog("Wystąpił błąd dostępu do pliku!\nPowrót");
                return;
            }

            buffer = new Windows.Storage.Streams.Buffer(bufferSize);
            System.Diagnostics.Debug.WriteLine("rozmiar buffer: " + buffer.Capacity);
            ulong count = fileSize / bufferSize;
            ulong rest = fileSize - (count * bufferSize);
            await this.Dispatcher.RunAsync(CoreDispatcherPriority.Normal, () =>
            {
                progress.Maximum = fileSize;
            });


            //---------------------------------------------------------------------przesylanie danych
            try
            {
                for (int i = 0; i < (int)count + 1; ++i)
                {
                    writer.WriteBuffer(await stream.ReadAsync(buffer, bufferSize, InputStreamOptions.None));
                    await writer.StoreAsync();
                    await reader.LoadAsync(1);
                    await this.Dispatcher.RunAsync(CoreDispatcherPriority.Normal, () =>
                    {
                        progress.Value += bufferSize;
                    });                 
                }
            }
            catch (Exception e)
            {
                disconnect();
                System.Diagnostics.Debug.WriteLine(e.StackTrace);
                showPopupDialog("Wystąpił błąd podczas przesyłania danych!\nPowrót");
                return;
            }

            disconnect();
            showPopupDialog("Wysyłanie zakończone!\nPowrót");
        }

        //-------------------------------------------------------------------------okienka dialogowe
        async private void popupDialog(String text)
        {
            MessageDialog msg = new MessageDialog(text);
            msg.Commands.Add(new UICommand("Ok", new UICommandInvokedHandler(CommandHandlers)));
            await msg.ShowAsync();
        }

        async private void showPopupDialog(String text)
        {
            await this.Dispatcher.RunAsync(CoreDispatcherPriority.Normal, () =>
            {
                popupDialog(text);
            });
        }

        //-------------------------------------------------------------------------handlery okienek dialogowych
        private void CommandHandlers(IUICommand commandLabel)
        {
            var Actions = commandLabel.Label;
            switch (Actions)
            {
                case "Ok":
                    resetUi();
                    break;
            }
        }

        //-------------------------------------------------------------------------przycisk anuluj
        private void cancel(object sender, RoutedEventArgs e)
        {
            if (started)
            {
                resetUi();
                disconnect();
            }
            else
                resetUi();
        }

        //-------------------------------------------------------------------------obsluga przycisku powrotu
        private void hardwareBackPressed(object sender, 
            Windows.Phone.UI.Input.BackPressedEventArgs e)
        {
            if (!started)
                if (Frame.CanGoBack)
                {
                    e.Handled = true;
                    Frame.GoBack();
                }
        }

        //-------------------------------------------------------------------------zamykanie polaczenia
        private void disconnect()
        {
            if (reader != null)
            {
                reader.Dispose();
                reader = null;
            }
            if (writer != null)
            {
                writer.Dispose();
                writer = null;
            }
            if (socket != null)
            {
                socket.Dispose();
                socket = null;
            }
            if (stream != null)
            {
                stream.Dispose();
                stream = null;
            }
            if (buffer != null)
                buffer = null;
            if (rfcommProvider != null)
            {
                rfcommProvider.StopAdvertising();
                rfcommProvider = null;
            }
            if (socketListener != null)
            {
                socketListener.Dispose();
                socketListener = null;
            }
        }

        //-------------------------------------------------------------------------resetowanie interfejsu
        private void resetUi()
        {
            started = false;
            if (messageId != -1)
                App.proximityDevice.StopPublishingMessage(messageId);
            messageId = -1;
            waiting.IsActive = false;
            progress.Value = 0;
            progress.Visibility = Visibility.Collapsed;
            tapImage.Visibility = Visibility.Collapsed;
            tapText.Visibility = Visibility.Collapsed;
            fileButton.Visibility = Visibility.Visible;
            if (file != null)
            {
                statusText.Text = "Nazwa pliku: " + file.Name + "\nRozmiar: " + fileSize + " bajtów";
                sendButton.Visibility = Visibility.Visible;
            }
        }
    }
}
