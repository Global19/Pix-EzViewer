/*
 * MIT License
 *
 * Copyright (c) 2020 ultranity
 * Copyright (c) 2019 Perol_Notsfsssf
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE
 */

package com.perol.asdpl.pixivez.viewmodel

import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import com.perol.asdpl.pixivez.objects.Toasty
import com.perol.asdpl.pixivez.repository.RetrofitRepository
import com.perol.asdpl.pixivez.responses.BookMarkDetailResponse
import com.perol.asdpl.pixivez.responses.Illust
import com.perol.asdpl.pixivez.services.PxEZApp
import com.perol.asdpl.pixivez.services.UnzipUtil
import com.perol.asdpl.pixivez.sql.AppDatabase
import com.perol.asdpl.pixivez.sql.IllustBeanEntity
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.io.File

class PictureXViewModel : BaseViewModel() {
    val illustDetail = MutableLiveData<Illust?>()
    val retrofitRepository: RetrofitRepository = RetrofitRepository.getInstance()
    val relatedPics = MutableLiveData<ArrayList<Illust>>()
    val likeIllust = MutableLiveData<Boolean>()
    val followUser = MutableLiveData<Boolean>()
    var tags = MutableLiveData<BookMarkDetailResponse.BookmarkDetailBean>()
    val progress = MutableLiveData<ProgressInfo>()
    val downloadGifSuccess = MutableLiveData<Boolean>()
    private val appDatabase = AppDatabase.getInstance(PxEZApp.instance)
    fun downloadZip(medium: String) {
        val zipPath =
            "${PxEZApp.instance.cacheDir.path}/${illustDetail.value!!.id}.zip"
        val file = File(zipPath)
        if (file.exists()) {
            Observable.create<Int> { ob ->
                UnzipUtil.UnZipFolder(
                    file,
                    PxEZApp.instance.cacheDir.path + File.separatorChar + illustDetail.value!!.id
                )
                ob.onNext(1)
            }.observeOn(AndroidSchedulers.mainThread()).subscribeOn(Schedulers.io()).subscribe({
                downloadGifSuccess.value = true
            }, {
                File(PxEZApp.instance.cacheDir.path + File.separatorChar + illustDetail.value!!.id).deleteRecursively()
                file.delete()
                reDownLoadGif(medium)
            }, {}).add()
        } else {
            reDownLoadGif(medium)
        }

    }

    fun loadGif(id: Long) = retrofitRepository.getUgoiraMetadata(id)

    private fun reDownLoadGif(medium: String) {
        val zipPath = "${PxEZApp.instance.cacheDir}/${illustDetail.value!!.id}.zip"
        val file = File(zipPath)
        progress.value = ProgressInfo(0, 0)
        retrofitRepository.getGIFFile(medium).subscribe({ response ->
            val inputStream = response.byteStream()
            Observable.create<Int> { ob ->
                val output = file.outputStream()
                println("----------")
                progress.value!!.all = response.contentLength()
                var bytesCopied: Long = 0
                val buffer = ByteArray(8 * 1024)
                var bytes = inputStream.read(buffer)
                while (bytes >= 0) {
                    output.write(buffer, 0, bytes)
                    bytesCopied += bytes
                    bytes = inputStream.read(buffer)
                    progress.value!!.now = bytesCopied
                    Observable.just(1).observeOn(AndroidSchedulers.mainThread()).subscribe {
                        progress.value = progress.value!!
                    }.add()
                }
                inputStream.close()
                output.close()
                println("+++++++++")
                UnzipUtil.UnZipFolder(
                    file,
                    PxEZApp.instance.cacheDir.path + File.separatorChar + illustDetail.value!!.id
                )
                ob.onNext(1)
            }.observeOn(AndroidSchedulers.mainThread()).subscribeOn(Schedulers.io()).subscribe({
                downloadGifSuccess.value = true
                println("wwwwwwwwwwwwwwwwwwwwww")
            }, {
                it.printStackTrace()
            }).add()

        }, {}, {}).add()
    }
    fun firstGet(param2: Illust){
            illustDetail.value = param2
            likeIllust.value = param2.is_bookmarked
            Thread {
                val ee = appDatabase.illusthistoryDao().getHistoryOne(param2.id)
                if (ee.isNotEmpty()) {
                    appDatabase.illusthistoryDao().deleteOne(ee[0])
                }
                appDatabase.illusthistoryDao().insert(
                    IllustBeanEntity(
                        null,
                        param2.image_urls.square_medium,
                        param2.id
                    )
                )
            }.start()
        }

    fun firstGet(toLong: Long) {
        retrofitRepository.getIllust(toLong).subscribe({
            illustDetail.value = it!!.illust
            likeIllust.value = it.illust.is_bookmarked
            Observable.just(1).observeOn(Schedulers.io()).subscribe { ot ->
                val ee = appDatabase.illusthistoryDao().getHistoryOne(it.illust.id)
                if (ee.isNotEmpty()) {
                    appDatabase.illusthistoryDao().deleteOne(ee[0])
                }
                appDatabase.illusthistoryDao().insert(
                    IllustBeanEntity(
                        null,
                        it.illust.image_urls.square_medium,
                        it.illust.id
                    )
                )
            }.add()
        }, {
            Toasty.warning(
                PxEZApp.instance,
                "查找的id不存在: $toLong",
                Toast.LENGTH_SHORT
            ).show()
            illustDetail.value = null
        }, {}).add()
    }

    fun getRelative(long: Long) {
        retrofitRepository.getIllustRelated(long).subscribe({
            relatedPics.value = it.illusts as ArrayList<Illust>?
        }, {}, {}).add()
    }

    fun fabClick() {
        val id = illustDetail.value!!.id
        val x_restrict = if (PxEZApp.R18Private && illustDetail.value!!.x_restrict == 1) {
            "private"
        } else {
            "public"
        }
        if (illustDetail.value!!.is_bookmarked) {
            retrofitRepository.postUnlikeIllust(id).subscribe({
                likeIllust.value = false
                illustDetail.value!!.is_bookmarked = false
            }, {

            }, {}, {}).add()
        } else {
            retrofitRepository.postLikeIllustWithTags(id, x_restrict, null).subscribe({
                likeIllust.value = true
                illustDetail.value!!.is_bookmarked = true
            }, {}, {}).add()
        }
    }

    fun fabOnLongClick() {
        if (illustDetail.value != null)
            retrofitRepository
                .getBookmarkDetail(illustDetail.value!!.id)
                .subscribe(
                    { tags.value = it.bookmark_detail }
                    , {}, {}).add()
        else {
            val a = illustDetail.value
            print(a)
        }
    }

    fun onDialogClick(boolean: Boolean) {
        val toLong = illustDetail.value!!.id
        if (!illustDetail.value!!.is_bookmarked) {
            val string = if (!boolean) {
                "public"
            } else {
                "private"
            }
            val arrayList = ArrayList<String>()
            tags.value?.let {
                for (i in it.tags) {
                    if (i.isIs_registered) {
                        arrayList.add(i.name)
                    }
                }
            }
            retrofitRepository.postLikeIllustWithTags(toLong, string, arrayList).subscribe({
                likeIllust.value = true
                illustDetail.value!!.is_bookmarked = true
            }, {}, {}).add()

        } else {
            retrofitRepository.postUnlikeIllust(toLong)
                .subscribe({
                    likeIllust.value = false
                    illustDetail.value!!.is_bookmarked = false
                }, {}, {}).add()
        }
    }

    fun likeUser() {
        val id = illustDetail.value!!.user.id
        if (!illustDetail.value!!.user.is_followed) {
            retrofitRepository.postFollowUser(id, "public").subscribe({
                followUser.value = true
                illustDetail.value!!.user.is_followed = true
            }, {}, {}).add()
        } else {
            retrofitRepository.postUnfollowUser(id).subscribe({
                followUser.value = false
                illustDetail.value!!.user.is_followed = false
            }, {}, {}
            ).add()
        }
    }

}

data class ProgressInfo(var now: Long, var all: Long)
