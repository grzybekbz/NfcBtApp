using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Threading.Tasks;
using System.Runtime.InteropServices.WindowsRuntime;
using Windows.Foundation;
using Windows.Foundation.Collections;
using Windows.ApplicationModel.Core;
using Windows.UI.Xaml;
using Windows.UI.Xaml.Controls;
using Windows.UI.Xaml.Controls.Primitives;
using Windows.UI.Xaml.Data;
using Windows.UI.Xaml.Input;
using Windows.UI.Xaml.Media;
using Windows.UI.Xaml.Navigation;
using Windows.UI.Core;
using Windows.UI.Popups;
using Windows.Devices.Bluetooth.Rfcomm;
using Windows.Devices.Enumeration;
using Windows.Networking.Sockets;
using Windows.Networking.Proximity;
using Windows.Storage.Streams;
using Windows.Storage;
using Windows.Networking;
using Windows.Devices.Bluetooth;
using Windows.Devices.Enumeration;

namespace NfcBluetoothApp
{
    public sealed partial class Receive : Page
    {
        private const String UUID = "00001101-0000-1000-8000-00805f9b34fb";
        private RfcommDeviceService service;
        private DataReader reader;
        private DataWriter writer;
        private StreamSocket socket;
        private IOutputStream stream;
        private IBuffer buffer;
        private const uint bufferSize = 16384;
        private StorageFile file;
        List<String> connectionData;
        private String deviceName;
        private String fileName;
        private long fileSize;
        private bool found = false;
        private bool started = false;

        public Receive()
        {
            this.InitializeComponent();
            this.NavigationCacheMode = NavigationCacheMode.Disabled;
            Windows.Phone.UI.Input.HardwareButtons.BackPressed += hardwareBackPressed;
            acceptDialog();
        }

        //-------------------------------------------------------------------------pobieranie danych o pliku
        protected override void OnNavigatedTo(NavigationEventArgs e)
        {
            base.OnNavigatedTo(e);
            connectionData = (List<String>)e.Parameter;
            deviceName = connectionData[0];
            fileName = connectionData[1];
            fileSize = Int64.Parse(connectionData[2]);
        }

        //-------------------------------------------------------------------------wyszukiwanie urzadzenia
        async private void findDevice()
        {
            try
            {
                PeerFinder.Start();
                PeerFinder.AlternateIdentities["Bluetooth:SDP"] = "{00001101-0000-1000-8000-00805f9b34fb}";
                var foundDevices = await PeerFinder.FindAllPeersAsync();

                foreach (var device in foundDevices)
                {
                    if (device.DisplayName.Equals(deviceName))
                    {
                        found = true;
                        await receiveFile(device);
                        break;
                    }
                }
                if (!found)
                    popupDialog("Nie znaleziono urządzenia!\nUpewnij się że urządzenia są sparowane\nPowrót");
            }
            catch (Exception e)
            {
                if ((uint)e.HResult == 0x8007048F)
                {
                    disconnect();
                    System.Diagnostics.Debug.WriteLine(e.StackTrace);
                    popupDialog("Bluetooth jest wyłączone!\nPowrót");
                    return;
                }
            }
        }

        async private Task receiveFile(PeerInformation device)
        {
            started = true;

            //-----------------------------------------------------------------------tworzenie socketu i strumieni
            try
            {
                socket = new Windows.Networking.Sockets.StreamSocket();
                StreamSocketControl control = socket.Control;
                control.KeepAlive = true;

                await socket.ConnectAsync(device.HostName, 
                    "{00001101-0000-1000-8000-00805f9b34fb}",
                    SocketProtectionLevel.BluetoothEncryptionAllowNullAuthentication);

                reader = new DataReader(socket.InputStream);
                writer = new DataWriter(socket.OutputStream);
            }
            catch (Exception e)
            {
                disconnect();
                System.Diagnostics.Debug.WriteLine(e.StackTrace);
                popupDialog("Wystąpił błąd połączenia!\nPowrót");
                return;
            }

            //-----------------------------------------------------------------------tworzenie nowego pliku
            try
            {
                file = await Windows.Storage.KnownFolders.PicturesLibrary.CreateFileAsync(
                    fileName, CreationCollisionOption.GenerateUniqueName);
            }
            catch (Exception e)
            {
                disconnect();
                System.Diagnostics.Debug.WriteLine(e.StackTrace);
                popupDialog("Wystąpił błąd podczas tworzenia pliku!\nPowrót");
                return;
            }

            //-----------------------------------------------------------------------strumien pliku
            try
            {
                stream = await file.OpenAsync(FileAccessMode.ReadWrite);
            }
            catch (Exception e)
            {
                disconnect();
                System.Diagnostics.Debug.WriteLine(e.StackTrace);
                popupDialog("Wystąpił błąd dostępu do pliku!\nPowrót");
                return;
            }

            long count = fileSize / bufferSize;
            long rest = fileSize - (count * bufferSize);
            uint bytesRead;
            progress.Maximum = fileSize;

            await System.Threading.Tasks.Task.Delay(50);

            //-----------------------------------------------------------------------przesylanie danych
            try
            {
                for (int i = 0; i < count; ++i)
                {
                    bytesRead = await reader.LoadAsync(bufferSize);
                    buffer = reader.ReadBuffer(bytesRead);
                    await stream.WriteAsync(buffer);
                    progress.Value += (int)bytesRead;
                    writer.WriteBoolean(true);
                    await writer.StoreAsync();
                }
                if (rest != 0)
                {
                    bytesRead = await reader.LoadAsync((uint)rest);
                    buffer = reader.ReadBuffer(bytesRead);
                    await stream.WriteAsync(buffer);
                    progress.Value += (int)bytesRead;
                    writer.WriteBoolean(true);
                    await writer.StoreAsync();
                }
            }
            catch (Exception e)
            {
                deleteFile();
                disconnect();
                System.Diagnostics.Debug.WriteLine(e.StackTrace);
                popupDialog("Wystąpił błąd podczas przesyłania danych!\nPowrót");
                return;
            }

            disconnect();
            popupDialog("Pobieranie zakończone!\nPowrót");
        }

        //-------------------------------------------------------------------------okno akceptacji pliku
        async private void acceptDialog()
        {
            await System.Threading.Tasks.Task.Delay(20);
            MessageDialog msg = new MessageDialog("Odebrac plik:\nNazwa: "
                + fileName + "\nRozmiar: " + fileSize + " bajtów?");

            msg.Commands.Add(new UICommand("Tak", new UICommandInvokedHandler(CommandHandlers)));
            msg.Commands.Add(new UICommand("Nie", new UICommandInvokedHandler(CommandHandlers)));

            await msg.ShowAsync();
        }

        //-------------------------------------------------------------------------okienka dialogowe
        async private void popupDialog(String text)
        {
            MessageDialog msg = new MessageDialog(text);
            msg.Commands.Add(new UICommand("Ok", new UICommandInvokedHandler(CommandHandlers)));
            await msg.ShowAsync();
        }

        //-------------------------------------------------------------------------handlery okienek dialog
        private void CommandHandlers(IUICommand commandLabel)
        {
            var Actions = commandLabel.Label;
            switch (Actions)
            {
                case "Tak":
                    findDevice();
                    break;
                case "Nie":
                    if (Frame.CanGoBack)
                        Frame.GoBack();
                    break;
                case "Ok":
                    if (Frame.CanGoBack)
                        Frame.GoBack();
                    break;
            }
        }

        //-------------------------------------------------------------------------obsluga przycisku powrotu
        private void hardwareBackPressed(object sender, Windows.Phone.UI.Input.BackPressedEventArgs e)
        {
            if (!started)
                if (Frame.CanGoBack)
                {
                    e.Handled = true;
                    Frame.GoBack();
                }
        }

        //-------------------------------------------------------------------------przycisk anuluj
        private void cancel(object sender, RoutedEventArgs e)
        {
            disconnect();
        }

        //-------------------------------------------------------------------------usuwanie pliku jesli przerwano
        async private void deleteFile()
        {
            if (file != null)
                await file.DeleteAsync();
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
            if (service != null)
                service = null;
            if (stream != null)
            {
                stream.Dispose();
                stream = null;
            }
            if (buffer != null)
                buffer = null;
        }
    }
}
