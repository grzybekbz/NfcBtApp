# NfcBtApp
  Aplikacja moblina do testowania NFC i Bluetooh

Repozytorium zawiera kody źródłowe dwóch aplikacji mobilnych stworzonych do nawiązywania łącznośći i przesyłania danych przy użyciu NFC oraz Bluetooth.

Aplikacje mogą zawierać błędy.

Narzędzia wykorzystane do napisania aplikacji na Androida:

-  Java

-  Android Studio 2.2.3 wraz z Android Software Development Kit (SDK) 7.0

Do napisania aplikacji Windows Phone wykorzystane zostały:

-  C#

-  środowisko Visual Studio Express 2013 oraz Windows SDK dla systemu Windows 8.1.
  
-  Windows Phone SDK nie posiada wbudowanych klas pozwalających na tworzenie wiadomości zgodnych ze standardem Nfc Forum. Dlatego do obsługi NDEF wykorzystana została biblioteka NDEF Library for Proximity APIs / NFC dostępna na stronie https://github.com/andijakl/ndef-nfc. Zapewnia ona między innymi odczytywanie wiadomości NDEF, wyodrębnianie informacji zawartych w rekordach, tworzenie rekordów poprzez podanie samej informacji, a także sprawdzanie treści wiadomości zgodnie ze standardami. Biblioteka wspiera obsługę najpopularniejszych typów rekordów w tym URI, tekst, Smart Poster i Android Application Record.

Opis implementacji.
	Pomimo, że technologia NFC umożliwia przesyłanie danych między dwoma urządzeniami, jej zasięg oraz prędkość transferu ogranicza ją do wysyłania niewielkiej ilości danych. W przypadku przesyłania dużych rozmiarów pliku lepiej sprawdza się Bluetooth. Możliwe jest zatem połączenie obu technologii w celu nawiązywania długotrwałych połączeń. Pozwala to na przyspieszenie całej procedury oraz znaczne zmniejszenie ingerencji użytkownika.
	
Ogólne założenia działania programu są bardzo proste. Aplikacja wysyła wiadomość NDEF w trybie P2P, zawierającą informacje potrzebne do nawiązania połączenia przez Bluetooth do drugiego urządzenia. Aplikacja wysyłająca tworzy serwer Bluetooth z którym łączy się odbierający, a następnie rozpoczynany jest transfer danych. Użytkownik jest informowany o zakończeniu przesyłania, a połączenie zostaje przerwane.
