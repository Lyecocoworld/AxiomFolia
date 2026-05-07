# Axiom Folia

This project is a fork of **Axiom Paper**, adapted to work with **Folia** and its region-based multithreading system.

## ⚠️ Project Status

This fork is functional, but remains experimental due to the specific behavior of Folia.

In particular, issues may occur when performing modifications over very large distances.

## 🧠 Why does this happen?

Folia runs different world regions on separate threads.  
When an operation affects multiple distant regions:

- it may be executed across **multiple threads simultaneously**
- synchronization becomes **complex and harder to guarantee**
- this can lead to unexpected behavior or errors

## 🧪 Risky Scenarios

Bugs are more likely to occur in situations such as:

- large-scale structure modifications  
- operations spanning distant chunks  
- actions involving multiple active regions at once  

## 🐛 Bug Reporting

If you encounter any notable issues, please report them.

Make sure to include:

- a brief description of the problem  
- steps to reproduce (if possible)  
- the **full error stacktrace**

📩 Discord contact: **lye_off**

## 📌 Notes

- This project is tightly coupled to Folia’s threading model  
- Some behaviors are inherently difficult to fix without deeper architectural changes  
- Feedback and contributions are welcome  

## Download the mod at
https://modrinth.com/plugin/axiom-paper-plugin/

## TIPS

To use axiom in your Folia / Folia fork server, along side downloading the Axiom Folia plugin you have to :

- Be an op on the server. If the player does not have op permissions, run `/op <playername>`. This player must then disconnect from the server and reconnect.

 - If you're using an alternative solution for permission management, you must give players the `axiom.default` permission.

 - If players continue to have issues, they can run the `/whynoaxiom` command for more information.
