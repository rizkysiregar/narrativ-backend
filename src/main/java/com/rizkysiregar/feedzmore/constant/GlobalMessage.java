package com.rizkysiregar.feedzmore.constant;

import lombok.Getter;

@Getter
public enum GlobalMessage {

    SUCCESS("200", "Permintaan Anda berhasil diproses."),
    CREATED("201", "Data baru berhasil ditambahkan!"),
    INTERNAL_ERROR("500", "Terjadi kendala pada server kami. Mohon tunggu sebentar dan coba lagi nanti."),
    BAD_REQUEST("400", "Maaf, ada kendala saat memproses data Anda. Periksa kembali input Anda."),
    TOO_MANY_REQUEST("429", "Terlalu banyak permintaan. Mohon istirahat sejenak sebelum mencoba lagi."),
    RATE_LIMIT("429", "Tunggu sebenetar ya, lagi rame banget. Coba lagi %d detik lagi ya!"),
    TOO_MANY_REQUEST_OTP("429", "Anda telah mencapai batas percobaan. Silakan minta kode OTP baru dalam 5 menit."),
    BAD_REQUEST_FILE_TO_LARGE("400", "Ukuran file terlalu besar. Mohon unggah file dengan ukuran yang lebih kecil."),
    NOT_FOUND("404", "Ups, data yang Anda cari tidak ditemukan."),
    UNAUTHORIZED("401", "Sesi Anda telah berakhir. Silakan masuk (login) kembali."),
    ACCESS_DENIED("403", "Access Denied: you don't have permission to access this resource"),
    GOOGLE_UNAUTHORIZED("401", "Invalid Google Token"),
    GOOGLE_TOKEN_MISS_MATCH("401", "Token client miss match"),
    GOOGLE_TOKEN_FAILED_TO_VERIFY("401", "Failed to verify Google token"),
    BAD_CREDENTIALS("401", "Email atau kata sandi yang Anda masukkan salah. Silakan coba lagi."),
    USER_DISABLED("401", "Akun Anda sudah dinonaktifkan. Silakan hubungi admin untuk mengaktifkan kembali akun Anda."),
    PARAMETER_NOT_FOUND("404", "Data pendukung tidak ditemukan. Mohon ulangi proses dari awal."),
    INVALID_OTP("400", "Kode OTP yang Anda masukkan salah. Periksa kembali kodenya, ya."),
    OTP_EXPIRED("404", "Kode OTP sudah tidak berlaku. Silakan minta kode yang baru."),
    INVALID_RESET_TOKEN("400", "Tautan perubahan kata sandi sudah tidak valid atau pernah digunakan."),
    USERNAME_ALREADY_TAKEN("400", "Nama pengguna ini sudah digunakan. Coba nama lain yang unik, ya!"),
    USER_NOT_FOUND("404", "User yang kamu cari tidak terdaftar"),
    USER_BANNED("401", "Akun Anda telah diblokir oleh admin."),
    USER_ALREADY_BANNED("409", "User ini sudah dalam status diblokir."),
    USER_NOT_BANNED("409", "User ini tidak sedang diblokir."),
    USER_ALREADY_DELETED("409", "User ini sudah menghapus akunnya sendiri sehingga tidak dapat dimoderasi."),
    CANNOT_BAN_SELF("403", "Anda tidak dapat memblokir akun Anda sendiri."),
    CANNOT_BAN_ADMIN("403", "Admin tidak dapat diblokir. Turunkan role admin tersebut terlebih dahulu."),
    CANNOT_CHANGE_OWN_ROLE("403", "Anda tidak dapat mengubah role akun Anda sendiri."),
    LAST_ADMIN("409", "Tidak dapat menurunkan role admin terakhir."),
    CONTENT_ALREADY_TAKEN_DOWN("409", "Konten ini sudah di-takedown."),
    CONTENT_NOT_TAKEN_DOWN("409", "Konten ini tidak sedang di-takedown."),
    POST_ALREADY_DELETED("409", "Post ini sudah dihapus oleh pemiliknya sehingga tidak dapat dimoderasi."),
    REPORT_ALREADY_EXISTS("409", "Anda sudah melaporkan ini sebelumnya."),
    CANNOT_REPORT_OWN("400", "Anda tidak dapat melaporkan diri sendiri atau konten Anda sendiri."),
    REPORT_DESCRIPTION_REQUIRED("400", "Deskripsi wajib diisi jika alasan laporan adalah OTHER."),
    REPORT_ALREADY_CLOSED("409", "Laporan ini sudah ditutup."),
    REPORT_ACTION_NOT_APPLICABLE("400", "Aksi ini tidak berlaku untuk jenis target laporan tersebut."),
    REPORT_NOTE_REQUIRED("400", "Catatan sepanjang 5 sampai 500 karakter wajib diisi untuk aksi ini."),
    PARAMETER_KEY_EXISTS("409", "Parameter dengan key ini sudah ada."),
    TEMPLATE_CODE_EXISTS("409", "Template dengan code ini sudah ada."),
    NOTHING_TO_UPDATE("400", "Tidak ada data yang diubah. Isi minimal satu field."),
    BROADCAST_FAILED("502", "Notifikasi gagal dikirim ke Firebase. Coba lagi nanti."),
    EMAIL_ALREADY_TAKEN("400", "Email ini sudah terdaftar. Silakan gunakan email lain atau masuk ke akun Anda."),
    ;


    private final String code;
    private final String message;

    GlobalMessage(String code, String message) {
        this.code = code;
        this.message = message;
    }

}
