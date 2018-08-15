using NdefLibrary.Ndef;
using System;
using System.IO;
using System.Linq;
using System.Collections.Generic;
using System.Runtime.InteropServices.WindowsRuntime;
using Windows.Foundation;
using Windows.Foundation.Collections;
using Windows.UI.Xaml;
using Windows.UI.Xaml.Controls;
using Windows.UI.Xaml.Controls.Primitives;
using Windows.UI.Xaml.Data;
using Windows.UI.Xaml.Input;
using Windows.UI.Xaml.Media;
using Windows.UI.Xaml.Navigation;
using Windows.UI.Core;
using Windows.Networking.Proximity;

namespace NfcBluetoothApp
{
    public sealed partial class MainPage : Page
    {
        private long messageId;

        public MainPage()
        {
            this.InitializeComponent();
            this.NavigationCacheMode = NavigationCacheMode.Required;
        }

        protected override void OnNavigatedTo(NavigationEventArgs e)
        {
            base.OnNavigatedTo(e);
            messageId = App.proximityDevice.SubscribeForMessage("NDEF", ReadMsg);
        }

        protected override void OnNavigatedFrom(NavigationEventArgs e)
        {
            base.OnNavigatedFrom(e);
            App.proximityDevice.StopSubscribingForMessage(messageId);
        }

        //-------------------------------------------------------------------------odczytaj wiadomosc NDEF
        private void ReadMsg(ProximityDevice sender, ProximityMessage msg)
        {
            var rawMsg = msg.Data.ToArray();
            var ndefMessage = NdefMessage.FromByteArray(rawMsg);

            if (ndefMessage[0].CheckSpecializedType(false) == typeof(NdefTextRecord))
            {
                var firstRecord = new NdefTextRecord(ndefMessage[0]);
                if (firstRecord.Text.Equals("no8g-Sj5i-i8aw6"))
                {
                    List<String> connectionData = new List<String>();
                    var secondRecord = new NdefTextRecord(ndefMessage[1]);
                    var thirdRecord = new NdefTextRecord(ndefMessage[2]);
                    var fourthRecord = new NdefTextRecord(ndefMessage[3]);
                    connectionData.Add(secondRecord.Text);
                    connectionData.Add(thirdRecord.Text);
                    connectionData.Add(fourthRecord.Text);
                    Receiving(connectionData);
                }
            }
        }

        //-------------------------------------------------------------------------przejdz do odbierania
        public async void Receiving(List<String> connectionData)
        {
            await Dispatcher.RunAsync(CoreDispatcherPriority.Normal, () =>
            {
                Frame.Navigate(typeof(Receive), connectionData);
            });
        }

        //-------------------------------------------------------------------------przycisk wyslij
        private void Sending(object sender, RoutedEventArgs e)
        {
            Frame.Navigate(typeof(Send));
        }
    }
}
