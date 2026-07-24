(function () {
    'use strict';

    var body = document.body;
    var header = document.getElementById('header');
    var footer = document.getElementById('footer');
    var main = document.getElementById('main');
    var articles = Array.prototype.slice.call(main.querySelectorAll('article'));
    var transitionDelay = 325;
    var locked = false;

    body.classList.add('is-loading');
    window.addEventListener('load', function () {
        window.setTimeout(function () {
            body.classList.remove('is-loading');
        }, 100);
    });

    function activeArticle() {
        return main.querySelector('article.active');
    }

    function setShellVisible(visible) {
        header.style.display = visible ? '' : 'none';
        footer.style.display = visible ? '' : 'none';
        main.style.display = visible ? 'none' : 'block';
    }

    function showArticle(id, initial) {
        var article = document.getElementById(id);
        if (!article || locked) {
            return;
        }
        locked = true;
        body.classList.add('is-article-visible');
        var current = activeArticle();
        if (current) {
            current.classList.remove('active');
            window.setTimeout(function () {
                current.style.display = 'none';
                reveal(article, initial);
            }, initial ? 0 : transitionDelay);
        } else {
            reveal(article, initial);
        }
    }

    function reveal(article, initial) {
        setShellVisible(false);
        article.style.display = 'block';
        window.requestAnimationFrame(function () {
            article.classList.add('active');
            window.scrollTo(0, 0);
            window.setTimeout(function () {
                locked = false;
            }, initial ? 0 : transitionDelay);
        });
    }

    function hideArticle(updateHash) {
        var article = activeArticle();
        if (!article || locked) {
            return;
        }
        locked = true;
        article.classList.remove('active');
        window.setTimeout(function () {
            article.style.display = 'none';
            body.classList.remove('is-article-visible');
            setShellVisible(true);
            window.scrollTo(0, 0);
            locked = false;
            if (updateHash && window.location.hash) {
                history.pushState(null, '', window.location.pathname + window.location.search);
            }
        }, transitionDelay);
    }

    main.style.display = 'none';
    articles.forEach(function (article) {
        article.style.display = 'none';
        var close = document.createElement('div');
        close.className = 'close';
        close.textContent = 'Close';
        close.setAttribute('role', 'button');
        close.setAttribute('aria-label', '关闭');
        close.addEventListener('click', function () {
            hideArticle(true);
        });
        article.appendChild(close);
        article.addEventListener('click', function (event) {
            event.stopPropagation();
        });
    });

    body.addEventListener('click', function () {
        if (body.classList.contains('is-article-visible')) {
            hideArticle(true);
        }
    });
    window.addEventListener('keyup', function (event) {
        if (event.key === 'Escape') {
            hideArticle(true);
        }
    });
    window.addEventListener('hashchange', function () {
        var id = window.location.hash.replace(/^#/, '');
        if (id) {
            showArticle(id, false);
        } else {
            hideArticle(false);
        }
    });

    var loginForm = document.getElementById('loginForm');
    var loginButton = document.getElementById('login2');
    var message = document.getElementById('msg');
    loginForm.addEventListener('submit', function (event) {
        event.preventDefault();
        loginButton.disabled = true;
        message.textContent = '正在登录…';
        var bodyParams = new URLSearchParams(new FormData(loginForm));
        fetch('/login', {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8'},
            body: bodyParams.toString(),
            credentials: 'same-origin'
        }).then(function (response) {
            if (!response.ok) {
                throw new Error('HTTP ' + response.status);
            }
            return response.json();
        }).then(function (result) {
            message.textContent = result.msg || '';
            if (result.code === 1) {
                window.location.href = '/aaa.html';
            }
        }).catch(function () {
            message.textContent = '登录请求失败，请稍后重试';
        }).finally(function () {
            loginButton.disabled = false;
        });
    });

    var initialId = window.location.hash.replace(/^#/, '');
    if (initialId) {
        showArticle(initialId, true);
    }
}());
