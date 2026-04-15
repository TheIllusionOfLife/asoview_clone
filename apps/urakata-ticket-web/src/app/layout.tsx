import "./globals.css";

export const metadata = {
  title: "UraKata Tickets",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  // The [locale] segment owns <html>/<body> so it can set lang per-locale.
  return children as React.ReactElement;
}
