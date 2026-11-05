# ALU Window v2.3 — New UI (One UI + iOS inspired)

## Kya badla
- **Bottom tab bar** (iOS style): Windows · Estimate · Cutting · AI · More
  - Selected tab par soft pill highlight (One UI feel)
- **Swipe se page flip**: Windows ⇄ Estimate ⇄ Cutting ab ViewPager2 par hain — ungli se left/right swipe karo
- **One UI large header**: blue top bar hata diya; bada page title ("Windows / Estimate / Cutting Plan") + stats subtitle,
  round icon chips (₹ price, customers, share, settings)
- **iOS page dots** header ke niche — kaunsa page khula hai dikhate hain
- **Share bottom sheet** (header ka share icon): WhatsApp · Excel · Cutting images
- **More menu** ab popup ki jagah **bottom sheet** me — icon + description ke saath
- Status bar ab background se match karti hai (light/dark dono me)
- Dark mode pehle jaisa automatic

## Tech
- `androidx.viewpager2:viewpager2:1.0.0` add hua
- `MainActivity`: TabLayout → ViewPager2 + custom bottom nav; `PageAdapter` pre-built views host karta hai
- Naye resources: `activity_main.xml` (rewrite), `sheet_share.xml`, `sheet_menu_row.xml`,
  `bg_bottom_nav`, `bg_nav_pill`, `bg_icon_chip`, `bg_sheet*`, `dot_on/off`,
  `ic_nav_windows/estimate/cutting`, `ic_v2_share`; `values-v23` / `values-night-v23` themes
- Koi calc / export / AI logic nahi chheda — sirf UI
- versionCode 25, versionName 2.3
